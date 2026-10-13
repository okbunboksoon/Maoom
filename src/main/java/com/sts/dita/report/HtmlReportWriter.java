package com.sts.dita.report;

import com.sts.dita.core.ValidationIssue;
import com.sts.dita.core.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class HtmlReportWriter {
    /** 독립 실행기처럼 사용자 정보가 없는 호출도 동일한 보고서를 만들 수 있게 유지한다. */
    public void write(ValidationResult result, Path output) throws Exception {
        write(result, output, null, null);
    }

    public void write(
            ValidationResult result,
            Path output,
            String userName,
            String userId) throws Exception {
        // 결과와 확인 상태 관리 스크립트를 하나의 이동 가능한 HTML 파일에 포함한다.
        StringBuilder rows = new StringBuilder();
        int index = 1;
        for (ValidationIssue issue : result.issues()) {
            // 고정 ID를 만들어 정렬 후에도 저장된 체크 상태가 같은 오류 항목에 연결되게 한다.
            String issueId = issueId(result.mapFile(), issue);
            rows.append("<tr data-issue-id=\"").append(issueId).append("\">")
                    .append(cell(index++, "center"))
                    .append(cell(issue.file().getFileName(), "center"))
                    .append(cell(issue.file(), "text")).append(cell(issue.line(), "center"))
                    .append(cell(issue.ruleId(), "text")).append(severityCell(issue.severity()))
                    .append(cell(issue.location(), "text"))
                    .append(cell(issue.message(), "text"))
                    .append("<td class=\"center\"><input class=\"done\" type=\"checkbox\" aria-label=\"확인 완료\"></td>")
                    .append("<td class=\"updated\"></td></tr>\n");
        }
        String template = """
<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>DITA Schematron 검사 결과</title>
<style>body{font-family:Segoe UI,Malgun Gothic,sans-serif;margin:24px;color:#202124}h1{font-size:22px}.summary,.toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:12px 0;padding:12px;background:#f4f6f8;border-radius:8px}.map-path{flex-basis:100%;color:#4b5563;word-break:break-all}.toolbar button{padding:7px 10px}.search{width:330px;padding:8px;margin:8px 0}table{border-collapse:collapse;width:100%;font-size:13px}th{cursor:pointer;background:#26384a;color:white;position:sticky;top:0;z-index:1;text-align:center}th,td{padding:7px;border:1px solid #d9dee3}td{text-align:left}tr:nth-child(even){background:#f8fafb}tr.completed{opacity:.68}.num{text-align:right;white-space:nowrap}.center{text-align:center}.hidden{display:none}.notice{color:#4b5563;font-size:12px}.severity{font-weight:600;white-space:nowrap;text-align:center}.severity-critical{color:#b42318}.severity-major{color:#d92d20}.severity-minor{color:#b54708}.severity-preference{color:#175cd3}.severity-none{color:#667085}</style></head>
<body><h1>DITA Schematron 검사 결과</h1><div class="summary"><span>검사 ditamap: <b>{{MAP_NAME}}</b></span><span>검사 파일: <b>{{FILE_COUNT}}</b></span><span>오류: <b id="total">{{ISSUE_COUNT}}</b></span><span>완료: {{FINISHED}}</span>{{USER_SUMMARY}}<span class="map-path">ditamap 경로: {{MAP_PATH}}</span></div>
<div class="toolbar"><button id="checkAll">전체 체크</button><button id="uncheckAll">전체 해제</button><label><input id="hideDone" type="checkbox"> 체크 항목 숨기기</label><button id="exportJson">상태 JSON 저장</button><button id="importJson">상태 JSON 불러오기</button><button id="saveHtml">상태 포함 HTML 저장</button><button id="resetState">상태 초기화</button><input id="jsonFile" type="file" accept="application/json,.json" hidden></div>
<input id="search" class="search" placeholder="파일, 규칙, 메시지 검색"><span id="shown"></span><div class="notice">변경 내용은 이 브라우저에 자동 저장됩니다. 다른 PC·작업자에게 전달할 때는 상태 JSON 또는 상태 포함 HTML을 저장하세요.</div>
<table id="result"><thead><tr><th data-type="num">순번</th><th>파일명</th><th>경로</th><th data-type="num">행</th><th>규칙 ID</th><th>심각도</th><th>위치</th><th>메시지</th><th>확인</th><th>수정 시각</th></tr></thead><tbody>{{ROWS}}</tbody></table>
<script id="embeddedState" type="application/json">{}</script>
<script>
(()=>{'use strict';
const table=document.querySelector('#result'),body=table.tBodies[0];
const storageKey='dita-sch-state:'+location.pathname;let sortState={col:-1,asc:true};
function emptyState(){return {version:1,report:document.title,savedAt:'',items:{}}}
function collect(){let data=emptyState();data.savedAt=new Date().toISOString();[...body.rows].forEach(r=>{data.items[r.dataset.issueId]={checked:r.querySelector('.done').checked,updatedAt:r.querySelector('.updated').textContent}});return data}
function apply(data){if(!data||!data.items)return;[...body.rows].forEach(r=>{let v=data.items[r.dataset.issueId];if(!v)return;r.querySelector('.done').checked=!!v.checked;r.querySelector('.updated').textContent=v.updatedAt||'';paint(r)});filterRows()}
function paint(r){r.classList.toggle('completed',r.querySelector('.done').checked)}
function persist(){let data=collect();try{localStorage.setItem(storageKey,JSON.stringify(data))}catch(e){}document.querySelector('#embeddedState').textContent=JSON.stringify(data).replace(/<\\/script/gi,'<\\/script')}
function touch(r){let checked=r.querySelector('.done').checked;r.querySelector('.updated').textContent=checked?new Date().toLocaleString('ko-KR'):'';paint(r);persist();filterRows()}
function download(name,text,type){let a=document.createElement('a');a.href=URL.createObjectURL(new Blob([text],{type}));a.download=name;document.body.appendChild(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000)}
function filterRows(){let q=document.querySelector('#search').value.toLowerCase(),hide=document.querySelector('#hideDone').checked,n=0;[...body.rows].forEach(r=>{let show=(!hide||!r.querySelector('.done').checked)&&r.textContent.toLowerCase().includes(q);r.classList.toggle('hidden',!show);if(show)n++});document.querySelector('#shown').textContent=' 표시 '+n+'건'}
body.addEventListener('change',e=>{let r=e.target.closest('tr');if(r)touch(r)});
table.querySelectorAll('th').forEach((h,i)=>h.onclick=()=>{sortState.asc=sortState.col===i?!sortState.asc:true;sortState.col=i;let rows=[...body.rows],num=h.dataset.type==='num';rows.sort((a,b)=>{let value=r=>{let c=r.cells[i],f=c.querySelector('input,select');return f?f.value:c.textContent.trim()},x=value(a),y=value(b),v=num?(Number(x)-Number(y)):x.localeCompare(y,'ko',{numeric:true});return sortState.asc?v:-v});rows.forEach(r=>body.appendChild(r))});
document.querySelector('#search').addEventListener('input',filterRows);document.querySelector('#hideDone').addEventListener('change',filterRows);
document.querySelector('#checkAll').onclick=()=>{[...body.rows].filter(r=>!r.classList.contains('hidden')).forEach(r=>{r.querySelector('.done').checked=true;touch(r)})};
document.querySelector('#uncheckAll').onclick=()=>{[...body.rows].forEach(r=>{r.querySelector('.done').checked=false;touch(r)})};
document.querySelector('#exportJson').onclick=()=>download('schematron-report-state.json',JSON.stringify(collect(),null,2),'application/json;charset=utf-8');
document.querySelector('#importJson').onclick=()=>document.querySelector('#jsonFile').click();document.querySelector('#jsonFile').onchange=e=>{let f=e.target.files[0];if(!f)return;let rd=new FileReader();rd.onload=()=>{try{apply(JSON.parse(rd.result));persist();alert('상태를 불러왔습니다.')}catch(x){alert('올바른 상태 JSON이 아닙니다.')}};rd.readAsText(f,'utf-8');e.target.value=''};
document.querySelector('#saveHtml').onclick=()=>{persist();download('schematron-report-with-state.html','<!doctype html>\\n'+document.documentElement.outerHTML,'text/html;charset=utf-8')};
document.querySelector('#resetState').onclick=()=>{if(!confirm('모든 체크 상태와 수정 시각을 초기화할까요?'))return;apply({items:Object.fromEntries([...body.rows].map(r=>[r.dataset.issueId,{checked:false,updatedAt:''}]))});persist()};
let initial={};try{initial=JSON.parse(document.querySelector('#embeddedState').textContent||'{}')}catch(e){}try{let local=JSON.parse(localStorage.getItem(storageKey)||'{}');if(local.savedAt&&(!initial.savedAt||local.savedAt>initial.savedAt))initial=local}catch(e){}apply(initial);persist();filterRows();
})();
</script></body></html>
""";
        // 보고서만 전달해도 검사 대상, 완료 시각, 실행 사용자를 식별할 수 있게 상단 요약에 넣는다.
        String finished = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(result.finishedAt());
        String displayUser = displayUser(userName, userId);
        String userSummary = displayUser.isBlank()
                ? ""
                : "<span>검사 사용자: <b>" + escape(displayUser) + "</b></span>";
        String html = template
                .replace("{{MAP_NAME}}", escape(result.mapFile().getFileName().toString()))
                .replace("{{MAP_PATH}}", escape(result.mapFile().toAbsolutePath().normalize().toString()))
                .replace("{{FILE_COUNT}}", Integer.toString(result.validatedFileCount()))
                .replace("{{ISSUE_COUNT}}", Integer.toString(result.issues().size()))
                .replace("{{FINISHED}}", escape(finished))
                .replace("{{USER_SUMMARY}}", userSummary)
                .replace("{{ROWS}}", rows.toString());
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }

    private static String displayUser(String userName, String userId) {
        String name = userName == null ? "" : userName.trim();
        String id = userId == null ? "" : userId.trim();
        return name.isBlank() ? id : name;
    }

    private static String cell(Object value, String cls) {
        return "<td class=\"" + cls + "\">" + escape(String.valueOf(value)) + "</td>";
    }

    private static String severityCell(String severity) {
        String code = severity == null || severity.isBlank() ? "none" : severity.trim().toLowerCase();
        String label = switch (code) {
            case "critical" -> "Critical";
            case "major" -> "Major";
            case "minor" -> "Minor";
            case "preference" -> "Preference";
            case "none" -> "none";
            default -> code;
        };
        return "<td class=\"severity severity-" + escape(code) + "\" data-severity=\""
                + escape(code) + "\">" + escape(label) + "</td>";
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String issueId(Path mapFile, ValidationIssue issue) throws Exception {
        Path base = mapFile.toAbsolutePath().getParent();
        Path file = issue.file().toAbsolutePath().normalize();
        String relative;
        try { relative = base.relativize(file).toString(); }
        catch (IllegalArgumentException ex) { relative = file.toString(); }
        String source = relative.replace('\\', '/') + "|" + issue.ruleId() + "|" + issue.location() + "|" + issue.message();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 16; i++) hex.append(String.format("%02x", digest[i]));
        return hex.toString();
    }
}
