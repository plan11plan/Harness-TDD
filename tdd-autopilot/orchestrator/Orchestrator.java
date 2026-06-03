// Orchestrator.java — tdd-autopilot '비-LLM 운전자'의 실제 코드 (자바 단일 파일).
//
// skill.md는 "이렇게 동작한다"는 계약(프롬프트)이라 LLM이 읽고 수동 운전 = 반자동(블로그 §9-4 🚩).
// 이 파일은 그 운전대를 코드로 가져온다.
//   - 제어 흐름(Phase1 → 레벨 루프 → R/G/R → 게이트 → 커밋 → 원장 → PR) = 결정적 자바 코드
//   - 게이트 판정 = 빌드 결과 코드 / 로그 신호로만 (LockedEvaluator, LLM 미개입)
//   - '사고'(테스트·구현·리팩터 작성)만 headless 역할 에이전트(claude -p)에 위임
//
// 실행 (Java 21 source-launch, 빌드 불필요. orchestrator/ 에서 실행하거나 --harness 지정):
//   java Orchestrator.java --feature "POST /api/v1/questions" --worktree <wt> --api POST_questions --dry-run
//   java Orchestrator.java --worktree <wt> --feature x --api POST_questions \
//        --check-gate red --pattern "*QuestionServiceTest" --layer domain
//   java Orchestrator.java --feature "POST /api/v1/questions" --worktree <wt> --api POST_questions
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class Orchestrator {

    static final Map<String, String> ROLE_FILES = Map.of(
            "strategist", "tdd-strategist.md",
            "designer", "tdd-designer.md",
            "red-author", "tdd-red-author.md",
            "green-implementer", "tdd-green-implementer.md",
            "refactorer", "tdd-refactorer.md",
            "auditor", "tdd-auditor.md");
    static final List<String> LEVEL_ORDER = List.of("unit", "integration", "e2e");

    // ── 값 객체 ──
    record Sh(int code, String out) {}
    record Verdict(boolean ok, String signal, String detail) {}
    static final class Escalation extends RuntimeException {  // 계약 변경/막힘 → 사람
        Escalation(String m) { super(m); }
    }

    // ── 상태 ──
    final Map<String, Object> m;
    final Map<String, Object> git;
    final Path wt, ws, agentsDir;
    final String feature, api;
    final int maxRecover;
    final boolean dry;
    final LockedEvaluator ev;
    final AgentRunner agent;
    final List<String> ledger = new ArrayList<>();

    @SuppressWarnings("unchecked")
    Orchestrator(Map<String, Object> manifest, Path harnessClaude, Path worktree,
                 String feature, String api, int maxRecover, boolean dry) {
        this.m = manifest;
        this.git = (Map<String, Object>) manifest.getOrDefault("git", new LinkedHashMap<>());
        this.wt = worktree;
        this.feature = feature;
        this.api = api;
        this.maxRecover = maxRecover;
        this.dry = dry;
        this.ws = worktree.resolve("_workspace").resolve(api);
        this.agentsDir = harnessClaude.resolve("agents");
        this.ev = new LockedEvaluator(manifest, worktree);
        this.agent = new AgentRunner(agentsDir, worktree, dry);
    }

    static void log(String s) { System.out.println("[orchestrator] " + s); }

    // ── 셸 실행 ──
    static Sh sh(String cmd, Path cwd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Sh(p.waitFor(), out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String readStr(Path p) {
        try { return Files.readString(p); } catch (IOException e) { throw new RuntimeException(e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 잠긴 평가자(locked-evaluator) — 모델 판단이 아니라 결과 코드/신호로만 판정.
    // ─────────────────────────────────────────────────────────────────────────
    static final class LockedEvaluator {
        final Map<String, Object> m;
        final Path wt;
        final List<Pattern> assertSig = new ArrayList<>(), compileSig = new ArrayList<>();

        @SuppressWarnings("unchecked")
        LockedEvaluator(Map<String, Object> m, Path wt) {
            this.m = m; this.wt = wt;
            for (Object s : (List<Object>) m.get("assertion_signals")) assertSig.add(Pattern.compile(s.toString()));
            for (Object s : (List<Object>) m.get("compile_error_signals")) compileSig.add(Pattern.compile(s.toString()));
        }

        Sh runTests(String pattern, StringBuilder cmdOut) {
            String sel = m.get("test_select").toString().replace("{pattern}", pattern);
            String cmd = m.get("test_cmd") + " " + sel;
            if (cmdOut != null) cmdOut.append(cmd);
            return sh(cmd, wt);
        }

        boolean any(List<Pattern> ps, String s) { for (Pattern p : ps) if (p.matcher(s).find()) return true; return false; }

        // 게이트 1 — RED: assertion에서만 통과(컴파일/런타임/무실패는 반려)
        Verdict gateRed(String pattern, String api, String layer) {
            StringBuilder c = new StringBuilder();
            Sh r = runTests(pattern, c);
            try {
                Path d = wt.resolve("_workspace").resolve(api).resolve(layer);
                Files.createDirectories(d);
                Files.writeString(d.resolve("gate_red.raw.txt"), "$ " + c + "\n\n" + r.out());
            } catch (IOException ignored) {}
            if (any(compileSig, r.out())) return new Verdict(false, "compile-error", "골격 부족 → red-author 재호출");
            if (r.code() == 0) return new Verdict(false, "no-failure", "실패 0 → RED 아님(케이스/기본값 강화)");
            if (any(assertSig, r.out())) return new Verdict(true, "assertion", "깨끗한 RED");
            return new Verdict(false, "non-assertion", "assertion 아닌 런타임 예외 → 기본값 안전화");
        }

        // 게이트 3 — GREEN / 유지: 해당 레벨 테스트 전부 초록(exit 0)
        Verdict gateGreen(String pattern) {
            Sh r = runTests(pattern, null);
            return new Verdict(r.code() == 0, r.code() == 0 ? "green" : "fail",
                    r.code() == 0 ? "" : tail(r.out(), 10));
        }

        // 게이트 5 — 커버리지 바닥(리스크 가중)
        Verdict gateCoverage(String pkgPrefix, double floorLine, double floorBranch) {
            sh(m.get("coverage_cmd").toString(), wt);
            Path xml = wt.resolve(m.get("coverage_report").toString());
            if (!Files.exists(xml)) return new Verdict(false, "no-report", "리포트 없음: " + xml);
            double[] lb = parseJacoco(xml, pkgPrefix);
            boolean ok = lb[0] >= floorLine && lb[1] >= floorBranch;
            return new Verdict(ok, "coverage",
                    String.format("line %.1f%%/%.0f · branch %.1f%%/%.0f", lb[0], floorLine, lb[1], floorBranch));
        }

        // 게이트 7 — 잠긴 목표 동작(에이전트 비공개 스위트)
        Verdict gateTargetBehavior() {
            Sh r = sh(m.get("test_cmd") + " " + m.get("target_behavior_run"), wt);
            return new Verdict(r.code() == 0, "target-behavior", r.code() == 0 ? "통과" : tail(r.out(), 10));
        }
    }

    static String tail(String s, int n) {
        String[] ls = s.strip().split("\n");
        int from = Math.max(0, ls.length - n);
        return String.join("\n", Arrays.copyOfRange(ls, from, ls.length));
    }

    static double[] parseJacoco(Path xml, String pkgPrefix) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setValidating(false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document doc = f.newDocumentBuilder().parse(xml.toFile());
            long[] line = {0, 0}, branch = {0, 0};
            NodeList pkgs = doc.getElementsByTagName("package");
            for (int i = 0; i < pkgs.getLength(); i++) {
                Element pkg = (Element) pkgs.item(i);
                if (!pkg.getAttribute("name").startsWith(pkgPrefix)) continue;
                NodeList counters = pkg.getChildNodes();
                for (int j = 0; j < counters.getLength(); j++) {
                    if (!(counters.item(j) instanceof Element c) || !c.getTagName().equals("counter")) continue;
                    long missed = Long.parseLong(c.getAttribute("missed"));
                    long covered = Long.parseLong(c.getAttribute("covered"));
                    if (c.getAttribute("type").equals("LINE")) { line[0] += missed; line[1] += covered; }
                    if (c.getAttribute("type").equals("BRANCH")) { branch[0] += missed; branch[1] += covered; }
                }
            }
            return new double[]{pct(line), pct(branch)};
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static double pct(long[] mc) { long t = mc[0] + mc[1]; return t == 0 ? 100.0 : 100.0 * mc[1] / t; }

    // ─────────────────────────────────────────────────────────────────────────
    // 역할 에이전트 러너 — headless 'claude -p'. (Agent SDK/API로 교체 가능)
    // ─────────────────────────────────────────────────────────────────────────
    static final class AgentRunner {
        final Path agentsDir, wt;
        final boolean dry;
        AgentRunner(Path agentsDir, Path wt, boolean dry) { this.agentsDir = agentsDir; this.wt = wt; this.dry = dry; }

        String run(String role, String task, String allowed) {
            String system = readStr(agentsDir.resolve(ROLE_FILES.get(role)));
            log("agent[" + role + "] ← " + task.split("\n")[0]);
            if (dry) return "(dry-run: 에이전트 미실행)";
            try {
                ProcessBuilder pb = new ProcessBuilder("claude", "-p", task,
                        "--append-system-prompt", system,
                        "--permission-mode", "acceptEdits",
                        "--allowedTools", allowed);
                pb.directory(wt.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (p.waitFor() != 0) throw new RuntimeException("agent[" + role + "] 실패: " + tail(out, 8));
                return out;
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 결정적 운전자
    // ─────────────────────────────────────────────────────────────────────────
    String commit(String msg) {
        if (dry) { log("[dry] commit: " + msg.split("\n")[0]); return "DRYRUN0"; }
        sh("git add -A", wt);
        sh("git commit -q -m \"" + msg.replace("\"", "'") + "\"", wt);
        return sh("git rev-parse --short HEAD", wt).out().strip();
    }

    @SuppressWarnings("unchecked")
    String levelPattern(String level) {
        Map<String, Object> levels = (Map<String, Object>) m.get("levels");
        Map<String, Object> lvl = (Map<String, Object>) levels.get(level);
        return lvl.get("pattern").toString().split(",")[0];
    }

    void runIncrement(String level) {
        String pat = levelPattern(level);
        log("=== 증분: " + api + " / " + level + "  (pattern=" + pat + ") ===");
        List<String> shas = new ArrayList<>();
        String firstFail = "(dry)";

        // RED → gate_red (반려 시 재호출 ≤ N)
        for (int attempt = 0; ; attempt++) {
            agent.run("red-author", "[" + feature + "] " + level
                    + " 레벨 실패 테스트 + 컴파일 골격을 작성하라. 골격은 안전한 기본값만 반환해 assertion에서 실패하게 하라.",
                    "Read,Write,Edit,Bash");
            Verdict v = dry ? new Verdict(true, "assertion", "(dry)") : ev.gateRed(pat, api, level);
            log("  게이트1 RED: " + v.signal() + " → " + (v.ok() ? "PASS" : "BLOCK") + " " + v.detail());
            if (v.ok()) { firstFail = v.signal(); break; }
            if (attempt >= maxRecover) throw new Escalation("RED 게이트 " + maxRecover + "회 반려: " + v.detail());
        }
        shas.add(commit("test(" + api + "): " + level + " RED + 컴파일 골격 (동결)"));

        // GREEN → gate_green
        for (int attempt = 0; ; attempt++) {
            agent.run("green-implementer", "[" + feature + "] " + level
                    + " 테스트를 통과하는 최소 구현. src/test 수정 금지(동결).", "Read,Write,Edit,Bash");
            Verdict v = dry ? new Verdict(true, "green", "") : ev.gateGreen(pat);
            log("  게이트3 GREEN: " + (v.ok() ? "PASS" : "BLOCK") + " " + v.detail());
            if (v.ok()) break;
            if (attempt >= maxRecover) throw new Escalation("GREEN 게이트 " + maxRecover + "회 실패");
        }
        shas.add(commit("feat(" + api + "): " + level + " 최소 구현 (GREEN)"));

        // REFACTOR → 유지 게이트 (빨강이면 revert)
        agent.run("refactorer", "[" + feature + "] " + level + " 초록을 유지하며 리팩터(중복 제거·명료화).",
                "Read,Write,Edit,Bash");
        Verdict v = dry ? new Verdict(true, "green", "") : ev.gateGreen(pat);
        log("  유지 게이트: " + (v.ok() ? "PASS" : "REVERT") + " " + v.detail());
        if (!v.ok()) sh("git checkout -- src/main", wt);   // 리팩터만 revert(테스트 동결 보존)
        shas.add(commit("refactor(" + api + "): " + level + " 구조 개선 (유지 게이트 통과)"));

        ledger.add("| " + api + " | " + level + " | " + firstFail + " | " + String.join("→", shas) + " |");
    }

    void run() {
        log("슬롯 로드: test_cmd=" + m.get("test_cmd") + " base=" + git.getOrDefault("base_branch", "(git 슬롯 미설정)"));
        // Phase 1: 전략 + 목표 동작 동결(사람 승인 관문은 호출부)
        agent.run("strategist", "[" + feature + "] 수용 기준에서 목표 동작 초안과 리스크·커버리지 바닥을 산출하라.", "Read,Write");
        log("Phase1: 목표 동작 동결(사람 승인 관문) — 동기 게이트");

        // Phase 2: designer 1회 후 단위 → 통합 → E2E
        agent.run("designer", "[" + feature + "] 수용 기준에서 GWT 케이스를 도출(구현 비참조).", "Read,Write");
        for (String level : LEVEL_ORDER) runIncrement(level);

        // API 완료 게이트: 커버리지 + 목표 동작 (바닥은 risk-profile 연동 지점)
        Verdict cov = dry ? new Verdict(true, "coverage", "(dry)") : ev.gateCoverage(pkgPrefix(), 80.0, 70.0);
        log("게이트5 커버리지: " + (cov.ok() ? "PASS" : "BLOCK") + " " + cov.detail());
        Verdict tb = dry ? new Verdict(true, "target", "(dry)") : ev.gateTargetBehavior();
        log("게이트7 목표 동작: " + (tb.ok() ? "PASS" : "BLOCK") + " " + tb.detail());

        // Phase 3: 감사 → PR
        agent.run("auditor", "[" + feature + "] 게이트 로그·원장·diff를 교차 대조해 감사.", "Read,Bash");
        openPr();
        writeLedger();
        log("완료: PR 생성. 머지는 사람(자동 머지 금지).");
    }

    String pkgPrefix() { return "jobterview/server"; }  // 예: 프로젝트별 매핑 지점

    @SuppressWarnings("unchecked")
    void openPr() {
        if (git.isEmpty()) { log("⚠️ slots에 git 블록 없음 → PR 생략(슬롯 보완 필요). 브랜치·커밋은 보존."); return; }
        if (Boolean.TRUE.equals(git.get("auto_merge"))) throw new Escalation("auto_merge=true 금지 — base 반영은 PR로 사람이");
        String branch = git.get("branch_prefix") + "/" + api;
        String pr = git.get("pr_cmd").toString()
                .replace("{base_branch}", git.get("base_branch").toString())
                .replace("{branch}", branch)
                .replace("{title}", feature + " (TDD autopilot)")
                .replace("{body_file}", ws.resolve("PR_BODY.md").toString());
        log("PR: " + pr);
        if (!dry) sh(pr, wt);
    }

    void writeLedger() {
        if (dry) { log("[dry] 신뢰 원장 기록 생략"); return; }
        try {
            Files.createDirectories(ws);
            List<String> lines = new ArrayList<>(List.of("| api | layer | first_fail | R/G/R |", "|---|---|---|---|"));
            lines.addAll(ledger);
            Files.writeString(ws.resolve("trust-ledger.md"), String.join("\n", lines));
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    // ── 슬롯/매니페스트 로드 (pyyaml 불필요 — 내장 미니 파서) ──
    static Map<String, Object> loadManifest(Path harnessClaude) {
        Path slots = harnessClaude.resolve("slots");
        Path p = slots.resolve("stack.manifest.yaml");
        if (!Files.exists(p)) {
            Path ex = slots.resolve("stack.manifest.example.yaml");
            if (!Files.exists(ex)) { System.err.println("매니페스트 없음: " + p); System.exit(1); }
            log("⚠️ stack.manifest.yaml 없음 → stack.manifest.example.yaml로 폴백(슬롯을 채우면 그걸 사용).");
            p = ex;
        }
        return parseYaml(readStr(p));
    }

    // 이 매니페스트 구조 전용 최소 YAML 파서(의존성 0).
    static List<String> splitTop(String s, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0; char q = 0;
        for (char ch : s.toCharArray()) {
            if (q != 0) { cur.append(ch); if (ch == q) q = 0; }
            else if (ch == '"' || ch == '\'') { q = ch; cur.append(ch); }
            else if (ch == '[' || ch == '{') { depth++; cur.append(ch); }
            else if (ch == ']' || ch == '}') { depth--; cur.append(ch); }
            else if (ch == sep && depth == 0) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        if (cur.length() > 0) out.add(cur.toString());
        List<String> res = new ArrayList<>();
        for (String x : out) if (!x.trim().isEmpty()) res.add(x.trim());
        return res;
    }

    static String stripComment(String line) {
        StringBuilder out = new StringBuilder(); char q = 0;
        for (char ch : line.toCharArray()) {
            if (q != 0) { out.append(ch); if (ch == q) q = 0; }
            else if (ch == '"' || ch == '\'') { q = ch; out.append(ch); }
            else if (ch == '#') break;
            else out.append(ch);
        }
        return out.toString();
    }

    static Object scalar(String v) {
        v = v.trim();
        if (v.startsWith("[") && v.endsWith("]")) {
            List<Object> l = new ArrayList<>();
            for (String x : splitTop(v.substring(1, v.length() - 1), ',')) l.add(scalar(x));
            return l;
        }
        if (v.startsWith("{") && v.endsWith("}")) {
            Map<String, Object> d = new LinkedHashMap<>();
            for (String pair : splitTop(v.substring(1, v.length() - 1), ',')) {
                int i = pair.indexOf(':');
                d.put(pair.substring(0, i).trim(), scalar(pair.substring(i + 1)));
            }
            return d;
        }
        if (v.length() >= 2 && v.charAt(0) == v.charAt(v.length() - 1) && (v.charAt(0) == '"' || v.charAt(0) == '\''))
            return v.substring(1, v.length() - 1);
        if (v.equals("true") || v.equals("false")) return Boolean.valueOf(v);
        return v;
    }

    static int indent(String s) { int i = 0; while (i < s.length() && s.charAt(i) == ' ') i++; return i; }

    static Map<String, Object> parseYaml(String text) {
        String[] lines = text.split("\n", -1);
        Map<String, Object> root = new LinkedHashMap<>();
        int i = 0, n = lines.length;
        while (i < n) {
            String raw = lines[i++];
            if (raw.trim().isEmpty() || raw.strip().startsWith("#") || indent(raw) != 0) continue;
            String line = stripComment(raw);
            int c = line.indexOf(':');
            if (c < 0) continue;
            String key = line.substring(0, c).trim(), val = line.substring(c + 1).trim();
            if (!val.isEmpty()) {
                root.put(key, scalar(val));
            } else {  // 들여쓴 자식 블록(levels/git)
                Map<String, Object> block = new LinkedHashMap<>();
                while (i < n) {
                    String child = lines[i];
                    if (child.trim().isEmpty()) { i++; continue; }
                    if (indent(child) == 0) break;
                    i++;
                    String cl = stripComment(child);
                    int cc = cl.indexOf(':');
                    if (cc < 0) continue;
                    String ck = cl.substring(0, cc).trim(), cv = cl.substring(cc + 1).trim();
                    block.put(ck, cv.isEmpty() ? new LinkedHashMap<String, Object>() : scalar(cv));
                }
                root.put(key, block);
            }
        }
        return root;
    }

    // ── 진입점 ──
    static Map<String, String> parseArgs(String[] a) {
        Map<String, String> o = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            if (!a[i].startsWith("--")) continue;
            String k = a[i].substring(2);
            if (i + 1 < a.length && !a[i + 1].startsWith("--")) o.put(k, a[++i]);
            else o.put(k, "true");
        }
        return o;
    }

    public static void main(String[] args) {
        Map<String, String> a = parseArgs(args);
        if (!a.containsKey("feature") || !a.containsKey("worktree")) {
            System.err.println("필수: --feature \"...\" --worktree <path>  [--api ...] [--dry-run] "
                    + "[--check-gate red|green|coverage|target --pattern ... --layer ...]");
            System.exit(1);
        }
        Path worktree = Paths.get(a.get("worktree"));
        Path harness = Paths.get(a.getOrDefault("harness", "../.claude"));
        String feature = a.get("feature");
        String api = a.getOrDefault("api",
                (feature.split(" ")[0] + "_" + feature.split(" ")[feature.split(" ").length - 1])
                        .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", ""));
        boolean dry = a.containsKey("dry-run");
        int maxRecover = Integer.parseInt(a.getOrDefault("max-recover", "3"));
        Map<String, Object> manifest = loadManifest(harness);

        // 게이트 단독 실측 — '잠긴 평가자가 진짜 코드'임을 바로 증명
        if (a.containsKey("check-gate")) {
            LockedEvaluator ev = new LockedEvaluator(manifest, worktree);
            String g = a.get("check-gate");
            Verdict v = switch (g) {
                case "red" -> ev.gateRed(a.get("pattern"), api, a.getOrDefault("layer", "domain"));
                case "green" -> ev.gateGreen(a.get("pattern"));
                case "coverage" -> ev.gateCoverage("jobterview/server", 80.0, 70.0);
                default -> ev.gateTargetBehavior();
            };
            System.out.println("게이트[" + g + "] → " + (v.ok() ? "PASS" : "BLOCK")
                    + " signal=" + v.signal() + " " + v.detail());
            System.exit(v.ok() ? 0 : 1);
        }

        try {
            new Orchestrator(manifest, harness, worktree, feature, api, maxRecover, dry).run();
        } catch (Escalation e) {
            log("⛔ 에스컬레이션(사람 필요): " + e.getMessage());
            System.exit(2);
        }
    }
}
