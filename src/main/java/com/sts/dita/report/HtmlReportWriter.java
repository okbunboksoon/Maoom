package com.sts.dita.report;

import com.sts.dita.core.ValidationIssue;
import com.sts.dita.core.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.BreakIterator;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HtmlReportWriter {
    /** 독립 실행기처럼 사용자 정보가 없는 호출도 동일한 보고서를 만들 수 있게 유지한다. */
    public void write(ValidationResult result, Path output) throws Exception {
        write(result, output, null, null, null);
    }

    public void write(
            ValidationResult result,
            Path output,
            String userName,
            String userId) throws Exception {
        write(result, output, null, userName, userId);
    }

    public void write(
            ValidationResult result,
            Path output,
            Long historyId,
            String userName,
            String userId) throws Exception {
        // 결과와 확인 상태 관리 스크립트를 하나의 이동 가능한 HTML 파일에 포함한다.
        StringBuilder rows = new StringBuilder();
        int index = 1;
        for (ValidationIssue issue : result.issues()) {
            // 고정 ID를 만들어 정렬 후에도 저장된 체크 상태가 같은 오류 항목에 연결되게 한다.
            String issueId = issueId(result.mapFile(), issue);
            rows.append("<tr class=\"level-").append(issue.level()).append("\" data-issue-id=\"")
                    .append(issueId).append("\">")
                    .append(cell(index++, "center"))
                    .append(fileCell(issue.file().getFileName().toString()))
                    .append(breadcrumbCell(issue.parentTitles(), issue.title())).append(cell(issue.line(), "center"))
                    .append(cell(issue.ruleId(), "text")).append(severityCell(issue.severity()))
                    .append(categoryCell(issue.category()))
                    .append(cell(issue.location(), "location-cell"))
                    .append(cell(issue.message(), "report-message"))
                    .append("<td class=\"memo-cell\"><div class=\"memo-wrap\"><input class=\"memo\" type=\"text\" size=\"1\" maxlength=\"500\" placeholder=\"작업 메모\" aria-label=\"수정 작업 메모\"></div></td>")
                    .append("<td class=\"center\"><input class=\"done\" type=\"checkbox\" aria-label=\"확인 완료\"></td>")
                    .append("<td class=\"updated center\"></td>")
                    .append("<td class=\"confirmer center\"></td></tr>\n");
        }
        String template = """
<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>DITA Schematron 검사 결과</title>
<style>.report-loading .table-wrap{visibility:hidden}.report-loading-notice{display:none;margin:18px 0;padding:24px;border:1px solid #dbe4ef;border-radius:8px;background:#fff;color:#64748b;text-align:center}.report-loading .report-loading-notice{display:block}.severity-error .severity-badge{background:#7f1d1d;color:#fff}.file-filter{padding:0;border:0;background:none;color:#2563b8;font:inherit;font-weight:600;text-decoration:underline;text-decoration-color:transparent;cursor:pointer}.file-filter:hover,.file-filter:focus{text-decoration-color:currentColor}.memo-cell{box-sizing:border-box;padding:5px 7px}.memo-wrap{box-sizing:border-box;width:100%;max-width:100%;overflow:hidden}.memo{box-sizing:border-box;display:block;width:100%;max-width:100%;min-width:0;padding:6px 8px;border:1px solid #cbd5e1;border-radius:5px;background:#fff;font:inherit}.memo:focus{border-color:#4b82d0;outline:0;box-shadow:0 0 0 2px rgba(75,130,208,.14)}th:nth-child(9),td:nth-child(9){width:22%}th:nth-child(10),td:nth-child(10){width:190px}th:nth-child(11),td:nth-child(11){width:56px}th:nth-child(12),td:nth-child(12){width:145px}th:nth-child(13),td:nth-child(13){width:90px}#toast{position:fixed;right:24px;bottom:24px;z-index:1000;max-width:420px;padding:11px 16px;border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,.2);color:#fff;font-size:13px;font-weight:700;opacity:0;pointer-events:none;transform:translateY(10px);transition:opacity .18s ease,transform .18s ease}#toast.show{opacity:1;transform:translateY(0)}#toast.success{background:#167044}#toast.error{background:#b42318}</style>
<style>body{font-family:Segoe UI,Malgun Gothic,sans-serif;margin:0;padding:24px;background:#f6f8fb;color:#202124}h1{margin:0 0 14px;color:#172033;font-size:23px}.summary,.toolbar{display:flex;align-items:center;flex-wrap:wrap;margin:12px 0}.summary{gap:10px 24px;padding:16px 18px;border:1px solid #dce4ee;border-radius:10px;background:#fff;box-shadow:0 2px 8px rgba(15,23,42,.06);color:#64748b;font-size:14px}.summary>span:not(.map-path){display:inline-flex;align-items:baseline;gap:5px;white-space:nowrap}.summary>span:not(.map-path) b{color:#172033;font-size:15px}.category-summary{display:flex;align-items:center;flex-basis:100%;gap:8px;flex-wrap:wrap;margin:4px -2px -2px;padding:12px;border-top:1px solid #e2e8f0;border-radius:0 0 7px 7px;background:#f8fafc}.category-summary-label{margin-right:2px;color:#64748b;font-size:12px;font-weight:700}.category-chip{display:inline-flex;align-items:center;gap:5px;padding:5px 10px;border:1px solid #dbe4ef;border-radius:999px;background:#fff;color:#475569;font:inherit;font-size:12px;line-height:1.2;white-space:nowrap;cursor:pointer}.category-chip b{color:#2563b8;font-size:13px}.category-chip:hover,.category-chip.active{border-color:#3978d1;background:#dbeafe;color:#1d4f91}.category-chip-total{border-color:#9fc1ee;background:#e5f0ff;color:#24538b}.severity-summary{border-top-style:dashed;margin-top:-4px}.map-path{flex-basis:100%;color:#4b5563;word-break:break-all}.toolbar{gap:8px;padding:12px 14px;border:1px solid #e1e7ef;border-radius:9px;background:#fff;box-shadow:0 1px 4px rgba(15,23,42,.04)}.toolbar button,.search-tools button{height:34px;padding:0 13px;border:1px solid #cbd5e1;border-radius:6px;background:#fff;color:#334155;font-family:inherit;font-size:12px;font-weight:600;cursor:pointer;transition:.15s ease}.toolbar button:hover,.search-tools button:hover{border-color:#8eadd2;background:#f1f6fc}.toolbar #saveHtml{border-color:#3978d1;background:#3978d1;color:#fff}.toolbar #saveHtml:hover{border-color:#2866bd;background:#2866bd}.toolbar label{display:inline-flex;align-items:center;gap:7px;margin-left:4px;color:#475569;font-size:13px;cursor:pointer}.toolbar input[type=checkbox],table input[type=checkbox]{width:16px;height:16px;margin:0;accent-color:#3978d1;cursor:pointer}.completion{margin-left:auto;padding:6px 10px;border-radius:999px;background:#e8f5ee;color:#167044;font-size:12px;font-weight:700}.search-tools{display:flex;align-items:center;gap:10px;margin:14px 0 6px}.search-tools .notice{margin:0}.search{box-sizing:border-box;width:400px;height:36px;padding:0 12px;border:1px solid #cbd5e1;border-radius:7px;background:#fff;color:#334155;font-family:inherit;font-size:13px;outline:none}.search:focus{border-color:#4b82d0;box-shadow:0 0 0 3px rgba(75,130,208,.14)}#shown{padding:5px 9px;border-radius:999px;background:#eaf0f7;color:#52647a;font-size:12px;font-weight:700}.notice{margin:0 0 10px;color:#64748b;font-size:12px}.collaboration-notice{margin:8px 0;padding:9px 12px;border:1px solid #93c5fd;border-radius:7px;background:#eff6ff;color:#1d4f91;font-size:12px;font-weight:600}.table-wrap{overflow:auto;border:1px solid #cfd8e3;border-radius:8px;background:#fff;box-shadow:0 2px 7px rgba(15,23,42,.05)}table{table-layout:fixed;border-collapse:separate;border-spacing:0;width:100%;min-width:1500px;background:#fff;font-size:13px}th{position:sticky;top:0;z-index:1;height:38px;padding:0 10px;border-right:1px solid #526477;border-bottom:1px solid #526477;background:#26384a;color:#fff;text-align:center;cursor:pointer;white-space:nowrap}td{padding:9px 10px;border-right:1px solid #e0e6ed;border-bottom:1px solid #e0e6ed;text-align:left;line-height:1.4;vertical-align:middle}th:last-child,td:last-child{border-right:0}tbody tr:nth-child(even) td{background:#f8fafc}tbody tr:hover td{background:#edf5ff}tbody tr.completed td{background:#eef9f2;color:#64748b}tbody tr.completed:hover td{background:#e5f5ec}tbody tr.completed .updated,tbody tr.completed .confirmer{color:#167044;font-weight:700}tbody tr.external-update td{animation:external-update 2.5s ease}@keyframes external-update{0%,45%{background:#fff3cd}100%{background:inherit}}th:nth-child(1),td:nth-child(1){width:52px}th:nth-child(2),td:nth-child(2){width:105px}th:nth-child(3),td:nth-child(3){width:20%}th:nth-child(4),td:nth-child(4){width:55px}th:nth-child(5),td:nth-child(5){width:145px}th:nth-child(6),td:nth-child(6){width:80px}th:nth-child(7),td:nth-child(7){width:90px}th:nth-child(8),td:nth-child(8){width:24%}th:nth-child(9),td:nth-child(9){width:25%}th:nth-child(10),td:nth-child(10){width:56px}th:nth-child(11),td:nth-child(11){width:145px}th:nth-child(12),td:nth-child(12){width:90px}.num{text-align:right;white-space:nowrap}.center{text-align:center}.hidden{display:none}.report-title,.location-cell,.report-message{overflow-wrap:anywhere;word-break:break-word}.breadcrumb{display:flex;align-items:baseline;gap:4px;flex-wrap:wrap}.breadcrumb-parent{display:inline-block;max-width:180px;overflow:hidden;color:#64748b;text-overflow:ellipsis;white-space:nowrap;vertical-align:bottom}.breadcrumb-separator{color:#94a3b8}.breadcrumb-current{color:#243b5a;font-weight:700}.severity{text-align:center}.severity-badge,.category-badge{display:inline-flex;align-items:center;justify-content:center;min-height:22px;padding:1px 7px;border-radius:999px;font-size:11px;font-weight:700;line-height:1.2;white-space:nowrap}.severity-critical .severity-badge{background:#fee2e2;color:#b42318}.severity-major .severity-badge{background:#ffead5;color:#c2410c}.severity-minor .severity-badge{background:#fef3c7;color:#a16207}.severity-preference .severity-badge{background:#dbeafe;color:#175cd3}.severity-none .severity-badge{background:#eef2f6;color:#667085}.category-badge{background:#edf4ff;color:#315f9c}</style></head>
<body class="report-loading" data-history-id="{{HISTORY_ID}}" data-map-name="{{MAP_NAME}}"><style>.level-1 td:nth-child(2),.level-1 td:nth-child(3){color:#1f4f8a;font-weight:700}</style><h1>DITA Schematron 검사 결과</h1><div class="summary"><span>검사 ditamap: <b>{{MAP_NAME}}</b></span><span>검사 파일: <b>{{FILE_COUNT}}</b></span><span>오류: <b id="total">{{ISSUE_COUNT}}</b></span><span>완료: <b id="total">{{FINISHED}}</b></span>{{USER_SUMMARY}}<div class="category-summary">{{CATEGORY_SUMMARY}}</div><div class="category-summary severity-summary">{{SEVERITY_SUMMARY}}</div><span class="map-path hidden">ditamap 경로: {{MAP_PATH}}</span></div>
<div class="toolbar"><button id="saveHtml">HTML 로컬 다운</button><button id="exportCsv">현재 결과 CSV</button><button id="checkAll">전체 체크</button><button id="uncheckAll">전체 해제</button><label><input id="hideDone" type="checkbox"> 체크 항목 숨기기</label><span id="completion" class="completion"></span><button id="exportJson" class="hidden">상태 JSON 저장</button><button id="importJson" class="hidden">상태 JSON 불러오기</button><button id="resetState" class="hidden">상태 초기화</button><input id="jsonFile" type="file" accept="application/json,.json" hidden></div>
<div class="search-tools"><input id="search" class="search" placeholder="파일, 타이틀, 규칙, 유형, 메시지 검색"><span id="shown"></span><button id="clearFileFilter" type="button" hidden>파일 필터 해제</button><button id="resetFilters" type="button">필터 초기화</button><span id="activeFilters" class="notice"></span></div><div id="stateNotice" class="notice">확인 상태와 작업 메모는 서버에 저장되어 모든 작업자에게 공유됩니다.</div><div id="collaborationNotice" class="collaboration-notice" hidden></div>
<div id="reportLoadingNotice" class="report-loading-notice">보고서 상태를 불러오는 중입니다.</div><div class="table-wrap"><table id="result"><thead><tr><th data-type="num">순번</th><th>파일명</th><th class="report-title">타이틀</th><th data-type="num">행</th><th>규칙 ID</th><th>심각도</th><th>유형</th><th>위치</th><th class="report-message">메시지</th><th>작업 메모</th><th>확인</th><th>수정 시각</th><th>확인자</th></tr></thead><tbody>{{ROWS}}</tbody></table></div><div id="toast" role="status" aria-live="polite"></div>
<style>.report-loading .table-wrap{visibility:hidden}.report-loading-notice{display:none;margin:18px 0;padding:24px;border:1px solid #dbe4ef;border-radius:8px;background:#fff;color:#64748b;text-align:center}.report-loading .report-loading-notice{display:block}.severity-error .severity-badge{background:#7f1d1d;color:#fff}.file-filter{padding:0;border:0;background:none;color:#2563b8;font:inherit;font-weight:600;text-decoration:underline;text-decoration-color:transparent;cursor:pointer}.file-filter:hover,.file-filter:focus{text-decoration-color:currentColor}.memo-cell{box-sizing:border-box;padding:5px 7px}.memo-wrap{box-sizing:border-box;width:100%;max-width:100%;overflow:hidden}.memo{box-sizing:border-box;display:block;width:100%;max-width:100%;min-width:0;padding:6px 8px;border:1px solid #cbd5e1;border-radius:5px;background:#fff;font:inherit}.memo:focus{border-color:#4b82d0;outline:0;box-shadow:0 0 0 2px rgba(75,130,208,.14)}th:nth-child(9),td:nth-child(9){width:22%}th:nth-child(10),td:nth-child(10){width:190px}th:nth-child(11),td:nth-child(11){width:56px}th:nth-child(12),td:nth-child(12){width:145px}th:nth-child(13),td:nth-child(13){width:90px}#toast{position:fixed;right:24px;bottom:24px;z-index:1000;max-width:420px;padding:11px 16px;border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,.2);color:#fff;font-size:13px;font-weight:700;opacity:0;pointer-events:none;transform:translateY(10px);transition:opacity .18s ease,transform .18s ease}#toast.show{opacity:1;transform:translateY(0)}#toast.success{background:#167044}#toast.error{background:#b42318}</style>
<script id="embeddedState" type="application/json">{}</script>
<script>
(()=>{'use strict';
const table=document.querySelector('#result'),body=table.tBodies[0],allRows=[...body.rows],rowByIssueId=new Map(allRows.map(row=>[row.dataset.issueId,row])),historyId=document.body.dataset.historyId,isLocalFile=location.protocol==='file:';
const storageKey='dita-sch-state:'+location.pathname,filterStorageKey=storageKey+':filters';let sortState={col:-1,asc:true},categoryFilter='',severityFilter='',fileFilter='',toastTimer,memoTimers=new WeakMap();
function emptyState(){return {version:1,report:document.title,savedAt:'',items:{}}}
function collect(){let data=emptyState();data.savedAt=new Date().toISOString();allRows.forEach(r=>{data.items[r.dataset.issueId]={checked:r.querySelector('.done').checked,updatedAt:r.querySelector('.updated').textContent,confirmerName:r.querySelector('.confirmer').textContent,memo:r.querySelector('.memo').value}});return data}
function applyItem(r,v){r.querySelector('.done').checked=!!v.checked;r.querySelector('.updated').textContent=formatTime(v.updatedAt);r.querySelector('.confirmer').textContent=v.confirmerName||'';r.querySelector('.memo').value=v.memo||'';paint(r)}
function apply(data){if(!data)return;let items=Array.isArray(data)?Object.fromEntries(data.map(v=>[v.issueId,v])):data.items;if(!items)return;Object.entries(items).forEach(([id,v])=>{let r=rowByIssueId.get(id);if(r)applyItem(r,v)});filterRows()}
function paint(r){r.classList.toggle('completed',r.querySelector('.done').checked)}
function persist(){let data=collect();try{localStorage.setItem(storageKey,JSON.stringify(data))}catch(e){}document.querySelector('#embeddedState').textContent=JSON.stringify(data).replace(/<\\/script/gi,'<\\/script')}
function formatTime(value){if(!value)return '';let date=new Date(value);return Number.isNaN(date.getTime())?value:date.toLocaleString('ko-KR')}
function showToast(message,error=false){let toast=document.querySelector('#toast');toast.textContent=message;toast.className=error?'show error':'show success';clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.className='',error?5000:2200)}
function updateCompletion(){let checked=allRows.filter(r=>r.querySelector('.done').checked).length,rate=allRows.length?checked*100/allRows.length:0;document.querySelector('#completion').textContent='확인 '+checked.toLocaleString('ko-KR')+' / '+allRows.length.toLocaleString('ko-KR')+'건, '+rate.toFixed(1)+'%'}
async function touch(r){let checkbox=r.querySelector('.done'),checked=checkbox.checked;checkbox.disabled=true;try{if(historyId&&!isLocalFile){let response=await fetch('/api/dita-validation/reports/'+historyId+'/state',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({issueId:r.dataset.issueId,checked})});if(!response.ok)throw new Error(await response.text()||'확인 상태 저장 실패');applyItem(r,await response.json())}else{applyItem(r,{checked,updatedAt:checked?new Date().toISOString():'',confirmerName:''})}persist();filterRows();showToast(checked?'확인 상태를 저장했습니다.':'확인 상태를 해제했습니다.')}catch(error){checkbox.checked=!checked;paint(r);updateCompletion();showToast(error.message||'확인 상태를 저장하지 못했습니다.',true)}finally{checkbox.disabled=false}}
async function loadServerState(notify=false){if(!historyId||isLocalFile)return;let response=await fetch('/api/dita-validation/reports/'+historyId+'/state',{cache:'no-store'});if(!response.ok)throw new Error(await response.text()||'확인 상태 조회 실패');let data=await response.json(),changed=[];if(notify){data.forEach(v=>{let r=rowByIssueId.get(v.issueId);if(r&&(r.querySelector('.done').checked!==!!v.checked||r.querySelector('.updated').textContent!==formatTime(v.updatedAt)||r.querySelector('.confirmer').textContent!==(v.confirmerName||'')||r.querySelector('.memo').value!==(v.memo||''))){changed.push(r);r.classList.remove('external-update');void r.offsetWidth;r.classList.add('external-update')}})}apply(data);persist();if(changed.length){let notice=document.querySelector('#collaborationNotice');notice.hidden=false;notice.textContent='다른 사용자가 '+changed.length+'개 항목의 확인 상태 또는 메모를 변경했습니다.'}}
function download(name,text,type){let a=document.createElement('a');a.href=URL.createObjectURL(new Blob([text],{type}));a.download=name;document.body.appendChild(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000)}
function reportDownloadName(){let now=new Date(),pad=value=>String(value).padStart(2,'0'),date=String(now.getFullYear()).slice(-2)+pad(now.getMonth()+1)+pad(now.getDate()),time=pad(now.getHours())+pad(now.getMinutes())+pad(now.getSeconds()),mapName=(document.body.dataset.mapName||'ditamap').replace(/\\.ditamap$/i,'').replace(/[\\/:*?"<>|]/g,'_').trim()||'ditamap';return date+'-'+time+'-'+mapName+'-SchematronReport.html'}
function prepareSearchIndex(){allRows.forEach(r=>{let title=r.cells[2]?.dataset.searchTitle||'';r.dataset.file=r.querySelector('.file-filter').dataset.file;r.dataset.search=[r.dataset.file,title,r.cells[4]?.textContent||'',r.cells[6]?.textContent||'',r.cells[8]?.textContent||'',r.querySelector('.memo').value].join(' ').toLowerCase()})}
function filterRows(){let input=document.querySelector('#search'),q=input.value.trim().toLowerCase(),hide=document.querySelector('#hideDone').checked,n=0;allRows.forEach(r=>{let show=(!hide||!r.querySelector('.done').checked)&&r.dataset.search.includes(q)&&(!fileFilter||r.dataset.file===fileFilter)&&(!categoryFilter||r.cells[6]?.dataset.category===categoryFilter)&&(!severityFilter||r.cells[5]?.dataset.severity===severityFilter);r.classList.toggle('hidden',!show);if(show)n++});document.querySelector('#shown').textContent=' 표시 '+n+'건';let conditions=[];if(fileFilter)conditions.push('파일 '+fileFilter);if(categoryFilter)conditions.push(categoryFilter);if(severityFilter)conditions.push(severityFilter);if(q)conditions.push('검색어 "'+input.value.trim()+'"');if(hide)conditions.push('미확인만');document.querySelector('#activeFilters').textContent=(conditions.length?conditions.join(' + '):'전체')+' · '+n+'건 표시';document.querySelector('#clearFileFilter').hidden=!fileFilter;try{localStorage.setItem(filterStorageKey,JSON.stringify({search:input.value,hide,fileFilter,categoryFilter,severityFilter}))}catch(e){}updateCompletion()}
function bindChips(selector,type){document.querySelectorAll(selector).forEach(chip=>chip.onclick=()=>{let value=chip.dataset[type]||'';if(type==='category')categoryFilter=categoryFilter===value?'':value;else severityFilter=severityFilter===value?'':value;document.querySelectorAll(selector).forEach(x=>x.classList.toggle('active',(type==='category'?categoryFilter:severityFilter)===(x.dataset[type]||'')));filterRows()})}
body.addEventListener('click',e=>{let button=e.target.closest('.file-filter');if(!button)return;fileFilter=button.dataset.file;filterRows()});
body.addEventListener('change',e=>{if(!e.target.classList.contains('done'))return;let r=e.target.closest('tr');if(r)touch(r)});
body.addEventListener('input',e=>{if(!e.target.classList.contains('memo'))return;let input=e.target,r=input.closest('tr');prepareSearchIndex();clearTimeout(memoTimers.get(input));memoTimers.set(input,setTimeout(()=>saveMemo(r),500))});
async function saveMemo(r){let input=r.querySelector('.memo'),memo=input.value;input.disabled=true;try{if(historyId&&!isLocalFile){let response=await fetch('/api/dita-validation/reports/'+historyId+'/memo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({issueId:r.dataset.issueId,memo})});if(!response.ok)throw new Error(await response.text()||'작업 메모 저장 실패');applyItem(r,await response.json())}persist();prepareSearchIndex();filterRows();showToast('작업 메모를 저장했습니다.')}catch(error){showToast(error.message||'작업 메모를 저장하지 못했습니다.',true)}finally{input.disabled=false}}
table.querySelectorAll('th').forEach((h,i)=>h.onclick=()=>{sortState.asc=sortState.col===i?!sortState.asc:true;sortState.col=i;let rows=[...body.rows],num=h.dataset.type==='num';rows.sort((a,b)=>{let value=r=>{let c=r.cells[i],f=c.querySelector('input,select');return f?f.value:c.textContent.trim()},x=value(a),y=value(b),v=num?(Number(x)-Number(y)):x.localeCompare(y,'ko',{numeric:true});return sortState.asc?v:-v});rows.forEach(r=>body.appendChild(r))});
let searchTimer;document.querySelector('#search').addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(filterRows,180)});document.querySelector('#hideDone').addEventListener('change',filterRows);
document.querySelector('#clearFileFilter').onclick=()=>{fileFilter='';filterRows()};
document.querySelector('#resetFilters').onclick=()=>{categoryFilter='';severityFilter='';fileFilter='';document.querySelector('#search').value='';document.querySelector('#hideDone').checked=false;document.querySelectorAll('.category-chip.active').forEach(chip=>chip.classList.remove('active'));filterRows()};
async function batchTouch(checked){let rows=[...body.rows].filter(r=>!r.classList.contains('hidden')&&r.querySelector('.done').checked!==checked);if(!rows.length){showToast('변경할 항목이 없습니다.');return}rows.forEach(r=>r.querySelector('.done').disabled=true);try{if(historyId&&!isLocalFile){let response=await fetch('/api/dita-validation/reports/'+historyId+'/state/batch',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({issueIds:rows.map(r=>r.dataset.issueId),checked})});if(!response.ok)throw new Error(await response.text()||'확인 상태 일괄 저장 실패');let items=Object.fromEntries((await response.json()).map(v=>[v.issueId,v]));rows.forEach(r=>applyItem(r,items[r.dataset.issueId]||{checked}))}else rows.forEach(r=>applyItem(r,{checked,updatedAt:checked?new Date().toISOString():'',confirmerName:''}));persist();filterRows();showToast(rows.length+'개 항목을 '+(checked?'확인 처리했습니다.':'확인 해제했습니다.'))}catch(error){showToast(error.message||'확인 상태를 일괄 저장하지 못했습니다.',true)}finally{rows.forEach(r=>r.querySelector('.done').disabled=false)}}
document.querySelector('#checkAll').onclick=()=>batchTouch(true);
document.querySelector('#uncheckAll').onclick=()=>batchTouch(false);
document.querySelector('#exportCsv').onclick=()=>{let headers=['순번','파일명','타이틀','행','규칙 ID','심각도','유형','위치','메시지','작업 메모','확인','수정 시각','확인자'],rows=[...body.rows].filter(r=>!r.classList.contains('hidden')),quote=value=>'"'+String(value??'').replace(/"/g,'""')+'"',lines=[headers.map(quote).join(',')];rows.forEach(r=>lines.push([...r.cells].map((cell,index)=>quote(index===9?cell.querySelector('.memo').value:index===10?(cell.querySelector('.done').checked?'확인':'미확인'):cell.textContent.trim())).join(',')));download(reportDownloadName().replace(/\\.html$/i,'-filtered.csv'),'\ufeff'+lines.join('\\r\\n'),'text/csv;charset=utf-8');showToast('현재 표시된 '+rows.length+'건을 CSV로 저장했습니다.')};
document.querySelector('#exportJson').onclick=()=>download('schematron-report-state.json',JSON.stringify(collect(),null,2),'application/json;charset=utf-8');
document.querySelector('#importJson').onclick=()=>document.querySelector('#jsonFile').click();document.querySelector('#jsonFile').onchange=e=>{let f=e.target.files[0];if(!f)return;let rd=new FileReader();rd.onload=()=>{try{apply(JSON.parse(rd.result));persist();alert('상태를 불러왔습니다.')}catch(x){alert('올바른 상태 JSON이 아닙니다.')}};rd.readAsText(f,'utf-8');e.target.value=''};
document.querySelector('#saveHtml').onclick=()=>{persist();download(reportDownloadName(),'<!doctype html>\\n'+document.documentElement.outerHTML,'text/html;charset=utf-8')};
document.querySelector('#resetState').onclick=()=>{if(!confirm('모든 체크 상태와 수정 시각, 확인자를 초기화할까요?'))return;apply({items:Object.fromEntries([...body.rows].map(r=>[r.dataset.issueId,{checked:false,updatedAt:'',confirmerName:''}]))});persist()};
bindChips('.category-summary:not(.severity-summary) .category-chip','category');bindChips('.severity-summary .category-chip','severity');table.querySelectorAll('.report-title,.location-cell,.report-message').forEach(cell=>cell.title=cell.textContent.trim());prepareSearchIndex();let initial={};try{initial=JSON.parse(document.querySelector('#embeddedState').textContent||'{}')}catch(e){}if(!historyId||isLocalFile){try{let local=JSON.parse(localStorage.getItem(storageKey)||'{}');if(local.savedAt&&(!initial.savedAt||local.savedAt>initial.savedAt))initial=local}catch(e){}}try{let filters=JSON.parse(localStorage.getItem(filterStorageKey)||'{}');document.querySelector('#search').value=filters.search||'';document.querySelector('#hideDone').checked=!!filters.hide;fileFilter=filters.fileFilter||'';categoryFilter=filters.categoryFilter||'';severityFilter=filters.severityFilter||'';document.querySelectorAll('.category-chip').forEach(chip=>{let type=chip.closest('.severity-summary')?'severity':'category',value=type==='category'?categoryFilter:severityFilter;chip.classList.toggle('active',(chip.dataset[type]||'')===value&&!!value)})}catch(e){}apply(initial);persist();prepareSearchIndex();filterRows();let finishLoading=()=>document.body.classList.remove('report-loading');if(isLocalFile){document.querySelector('#stateNotice').textContent='로컬 HTML의 확인 상태와 작업 메모는 이 파일에만 반영되며 확인자 이름은 기록되지 않습니다. 작업 후 HTML 로컬 다운으로 새 파일을 저장해 주세요.';finishLoading()}else{loadServerState().catch(error=>{document.querySelector('#stateNotice').textContent='서버 확인 상태를 불러오지 못했습니다: '+error.message}).finally(finishLoading)}if(historyId&&!isLocalFile)setInterval(()=>{if(!document.hidden)loadServerState(true).catch(()=>{})},10000);
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
                .replace("{{HISTORY_ID}}", historyId == null ? "" : historyId.toString())
                .replace("{{MAP_PATH}}", escape(result.mapFile().toAbsolutePath().normalize().toString()))
                .replace("{{FILE_COUNT}}", Integer.toString(result.validatedFileCount()))
                .replace("{{ISSUE_COUNT}}", Integer.toString(result.issues().size()))
                .replace("{{CATEGORY_SUMMARY}}", categorySummary(result))
                .replace("{{SEVERITY_SUMMARY}}", severitySummary(result))
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

    private static String fileCell(String fileName) {
        return "<td class=\"center\"><button type=\"button\" class=\"file-filter\" data-file=\""
                + escape(fileName) + "\" title=\"이 파일의 오류만 보기\">" + escape(fileName) + "</button></td>";
    }

    private static String severityCell(String severity) {
        String code = severity == null || severity.isBlank() ? "none" : severity.trim().toLowerCase();
        String label = switch (code) {
            case "critical" -> "Critical";
            case "major" -> "Major";
            case "minor" -> "Minor";
            case "preference" -> "Preference";
            case "error" -> "Error";
            case "none" -> "none";
            default -> code;
        };
        return "<td class=\"severity severity-" + escape(code) + "\" data-severity=\""
                + escape(code) + "\"><span class=\"severity-badge\">" + escape(label) + "</span></td>";
    }

    private static String categoryCell(String category) {
        String code = category == null || category.isBlank() ? "uncategorized" : category.trim().toLowerCase();
        return "<td class=\"category center\" data-category=\"" + escape(code) + "\"><span class=\"category-badge\">"
                + escape(categoryLabel(code)) + "</span></td>";
    }

    private static String categoryLabel(String code) {
        return switch (code) {
            case "image" -> "이미지";
            case "xref" -> "상호참조";
            case "table" -> "표";
            case "ditamap" -> "DITAMAP";
            case "structure" -> "구조";
            case "whitespace" -> "공백";
            case "element" -> "태그";
            case "language" -> "언어";
            case "unit" -> "숫자&단위";
            case "index" -> "색인";
            case "system" -> "시스템";
            case "uncategorized" -> "미분류";
            default -> code;
        };
    }

    private static String categorySummary(ValidationResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] order = {"image", "xref", "ditamap", "structure", "whitespace", "element",
                "language", "unit", "index", "table", "system", "uncategorized"};
        for (String code : order) counts.put(code, 0);
        for (ValidationIssue issue : result.issues()) {
            String code = issue.category() == null || issue.category().isBlank()
                    ? "uncategorized" : issue.category().trim().toLowerCase();
            counts.merge(code, 1, Integer::sum);
        }
        StringBuilder summary = new StringBuilder("<span class=\"category-summary-label\">유형별</span>")
                .append("<button type=\"button\" class=\"category-chip category-chip-total\">전체 <b>")
                .append(result.issues().size()).append("</b>건</button>");
        counts.forEach((code, count) -> {
            if (count > 0) {
                summary.append("<button type=\"button\" class=\"category-chip\" data-category=\"")
                        .append(escape(code)).append("\">")
                        .append(escape(categoryLabel(code))).append(" <b>")
                        .append(count).append("</b></button>");
            }
        });
        return summary.toString();
    }

    private static String severitySummary(ValidationResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : List.of("critical", "major", "minor", "preference", "none")) counts.put(code, 0);
        for (ValidationIssue issue : result.issues()) {
            String code = issue.severity() == null || issue.severity().isBlank()
                    ? "none" : issue.severity().trim().toLowerCase();
            counts.merge(code, 1, Integer::sum);
        }
        StringBuilder summary = new StringBuilder("<span class=\"category-summary-label\">심각도별</span>")
                .append("<button type=\"button\" class=\"category-chip category-chip-total\">전체 <b>")
                .append(result.issues().size()).append("</b>건</button>");
        counts.forEach((code, count) -> {
            if (count > 0) {
                String label = "none".equals(code) ? "none"
                        : Character.toUpperCase(code.charAt(0)) + code.substring(1);
                summary.append("<button type=\"button\" class=\"category-chip\" data-severity=\"")
                        .append(escape(code)).append("\">").append(escape(label)).append(" <b>")
                        .append(count).append("</b></button>");
            }
        });
        return summary.toString();
    }

    private static String breadcrumbCell(List<String> parentTitles, String currentTitle) {
        String current = currentTitle == null || currentTitle.isBlank() ? "(제목 없음)" : currentTitle.trim();
        List<String> parents = parentTitles == null ? List.of() : parentTitles.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        String fullBreadcrumb = String.join(" › ", parents.isEmpty()
                ? List.of(current)
                : java.util.stream.Stream.concat(parents.stream(), java.util.stream.Stream.of(current)).toList());
        StringBuilder html = new StringBuilder("<td class=\"report-title\" data-search-title=\"")
                .append(escape(fullBreadcrumb)).append("\" title=\"")
                .append(escape(fullBreadcrumb)).append("\"><span class=\"breadcrumb\" dir=\"auto\">");
        for (String parent : parents) {
            html.append("<span class=\"breadcrumb-parent\" title=\"").append(escape(parent))
                    .append("\">").append(escape(truncateCharacters(parent, 25))).append("</span>")
                    .append("<span class=\"breadcrumb-separator\" aria-hidden=\"true\">›</span>");
        }
        return html.append("<span class=\"breadcrumb-current\">").append(escape(current))
                .append("</span></span></td>").toString();
    }

    private static String truncateCharacters(String value, int maximumCharacters) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        int end = 0;
        for (int count = 0; count < maximumCharacters; count++) {
            int next = iterator.next();
            if (next == BreakIterator.DONE) return value;
            end = next;
        }
        return iterator.next() == BreakIterator.DONE ? value : value.substring(0, end).stripTrailing() + "…";
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
