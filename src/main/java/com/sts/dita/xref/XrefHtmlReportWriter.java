package com.sts.dita.xref;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class XrefHtmlReportWriter {
    public void write(XrefValidationResult result, Path output) throws Exception {
        StringBuilder rows = new StringBuilder(); int n = 1;
        for (XrefCheckResult x : result.xrefs()) {
            rows.append("<tr class=\"").append(x.hasError() ? "error" : "ok").append("\">")
                    .append(td(n++)).append(td(x.sourceFile().getFileName())).append(td(x.line()))
                    .append(td(x.href())).append(td(x.targetFile() == null ? "" : x.targetFile()))
                    .append(td(x.targetId())).append(td(x.formatStatus())).append(td(x.fileStatus()))
                    .append(td(x.mapStatus())).append(td(x.idStatus())).append(td(x.message())).append("</tr>\n");
        }
        String html = """
<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>DITA xref 검사 결과</title>
<style>body{font-family:Segoe UI,Malgun Gothic,sans-serif;margin:24px;color:#202124}.summary{display:flex;gap:24px;padding:12px;background:#f4f6f8;border-radius:8px}.tools{margin:12px 0}input{padding:8px;width:360px}table{border-collapse:collapse;width:100%;font-size:13px}th,td{border:1px solid #d9dee3;padding:7px;text-align:left}th{background:#26384a;color:#fff;cursor:pointer;position:sticky;top:0}.error{background:#fff1f1}.hidden{display:none}</style></head><body>
<h1>DITA xref 검사 결과</h1><div class="summary"><span>연결 파일 <b>{{FILES}}</b></span><span>전체 xref <b>{{TOTAL}}</b></span><span>오류 xref <b>{{ERRORS}}</b></span></div>
<div class="tools"><input id="search" placeholder="파일, href, 결과 검색"> <label><input id="errors" type="checkbox" style="width:auto"> 오류만 보기</label></div>
<table id="result"><thead><tr><th>순번</th><th>원본 파일</th><th>행</th><th>href</th><th>대상 파일</th><th>대상 ID</th><th>형식</th><th>파일 존재</th><th>map 연결</th><th>ID 존재</th><th>결과</th></tr></thead><tbody>{{ROWS}}</tbody></table>
<script>(()=>{const t=document.querySelector('#result'),b=t.tBodies[0],q=document.querySelector('#search'),e=document.querySelector('#errors');function f(){let s=q.value.toLowerCase();[...b.rows].forEach(r=>{let v=(!e.checked||r.classList.contains('error'))&&r.textContent.toLowerCase().includes(s);r.classList.toggle('hidden',!v)})}q.oninput=f;e.onchange=f;t.querySelectorAll('th').forEach((h,i)=>h.onclick=()=>{let a=h.dataset.asc!=='1';t.querySelectorAll('th').forEach(x=>delete x.dataset.asc);h.dataset.asc=a?'1':'0';[...b.rows].sort((x,y)=>{let p=x.cells[i].textContent,z=y.cells[i].textContent,v=p.localeCompare(z,'ko',{numeric:true});return a?v:-v}).forEach(r=>b.appendChild(r))});f()})();</script></body></html>
""".replace("{{FILES}}", Integer.toString(result.linkedFileCount()))
                .replace("{{TOTAL}}", Integer.toString(result.xrefs().size()))
                .replace("{{ERRORS}}", Long.toString(result.errorCount())).replace("{{ROWS}}", rows.toString());
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }
    private static String td(Object value) { return "<td>" + esc(String.valueOf(value)) + "</td>"; }
    private static String esc(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
}
