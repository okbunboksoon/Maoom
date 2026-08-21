const colorCheckState = {
    items: [],
    filteredItems: [],
    sortKey: 'regDt',
    sortDirection: 'desc',
    editingName: ''
};

const berAsisTobeState = {
    items: [],
    filteredItems: [],
    editingCell: null
};

/*
 * 관리자 > QSG DB 화면 상태.
 *
 * API 응답은 /admin/qsg-db/items에서 오며, 원본 QSG_DB.xml의
 * <entry hash="..."><term lang="...">...</term></entry> 구조를
 * hash + lang + term 행 목록으로 펼친 값이다.
 *
 * 현재 화면은 읽기 전용이다. 수정/업로드를 붙일 때는 BER DB 상태값처럼
 * editingCell, save/delete API를 추가하면 된다. 엑셀 업로드는
 * hash + lang 기준으로 QSG_DB.xml의 term 값을 수정/추가한다.
 */
const qsgDbState = {
    items: [],
    filteredItems: []
};

/*
 * 관리자 > Replace Symbol DB 화면 상태.
 *
 * API 응답은 /admin/replace-dark-symbol/items에서 오며,
 * replace_dark_symbol.xml의 <replace from="..." to="..."/> 구조를
 * fromSymbol + toSymbol 행으로 펼친 값이다.
 *
 * From/To 셀은 BER DB처럼 더블클릭으로 바로 수정하고,
 * 저장된 DB 값은 배치 실행 직전 xsl/replace_dark_symbol.xml로 다시 생성된다.
 */
const replaceDarkSymbolState = {
    items: [],
    filteredItems: [],
    editingCell: null
};

const projectLogState = {
    items: [],
    filteredItems: []
};

const userState = {
    items: [],
    filteredItems: [],
    editingCell: null
};

const accountBackdrop =
    document.getElementById('accountDialogBackdrop');
const accountForm =
    document.getElementById('accountForm');
const accountUserId =
    document.getElementById('accountUserId').value;
const accountProfileFile =
    document.getElementById('accountProfileFile');
const tableBody =
    document.getElementById('colorCheckTableBody');
const summary =
    document.getElementById('colorCheckSummary');
const refreshButton =
    document.getElementById('colorCheckRefresh');
const editForm =
    document.getElementById('colorCheckEditForm');
const drawingNameInput =
    document.getElementById('colorCheckDrawingName');
const checkValueSelect =
    document.getElementById('colorCheckValue');
const cancelButton =
    document.getElementById('colorCheckCancel');
const importForm =
    document.getElementById('colorCheckImportForm');
const importFileInput =
    document.getElementById('colorCheckImportFile');
const importButton =
    document.getElementById('colorCheckImportButton');
const importResult =
    document.getElementById('colorCheckImportResult');
const colorCheckSection =
    document.getElementById('colorCheckSection');
const berAsisTobeSection =
    document.getElementById('berAsisTobeSection');
// QSG DB 메뉴 클릭 시 보여줄 관리자 섹션. 실제 HTML은 admin/section/qsgDb.html에 있다.
const qsgDbSection =
    document.getElementById('qsgDbSection');
const replaceDarkSymbolSection =
    document.getElementById('replaceDarkSymbolSection');
const projectLogSection =
    document.getElementById('projectLogSection');
const userSection =
    document.getElementById('userSection');
const projectLogTableBody =
    document.getElementById('projectLogTableBody');
const projectLogSummary =
    document.getElementById('projectLogSummary');
const colorTotalCount =
    document.getElementById('colorTotalCount');
const colorCheckedCount =
    document.getElementById('colorCheckedCount');
const colorUncheckedCount =
    document.getElementById('colorUncheckedCount');
const colorFilteredCount =
    document.getElementById('colorFilteredCount');
const berAsisTobeTableBody =
    document.getElementById('berAsisTobeTableBody');
const berAsisTobeSummary =
    document.getElementById('berAsisTobeSummary');
const berAsisTobeRefresh =
    document.getElementById('berAsisTobeRefresh');
const berAsisTobeImportForm =
    document.getElementById('berAsisTobeImportForm');
const berAsisTobeImportFile =
    document.getElementById('berAsisTobeImportFile');
const berAsisTobeImportButton =
    document.getElementById('berAsisTobeImportButton');
const berAsisTobeImportResult =
    document.getElementById('berAsisTobeImportResult');
const berSentenceImportToggle =
    document.getElementById('berSentenceImportToggle');
const berTotalCount =
    document.getElementById('berTotalCount');
const berEuCount =
    document.getElementById('berEuCount');
const berUsCount =
    document.getElementById('berUsCount');
const berFilteredCount =
    document.getElementById('berFilteredCount');
// QSG DB 화면의 테이블 본문, 요약 문구, 상단 통계 카드.
const qsgDbTableBody =
    document.getElementById('qsgDbTableBody');
const qsgDbSummary =
    document.getElementById('qsgDbSummary');
const qsgDbRefresh =
    document.getElementById('qsgDbRefresh');
const qsgDbImportForm =
    document.getElementById('qsgDbImportForm');
const qsgDbImportFile =
    document.getElementById('qsgDbImportFile');
const qsgDbImportButton =
    document.getElementById('qsgDbImportButton');
const qsgDbImportResult =
    document.getElementById('qsgDbImportResult');
const qsgTotalCount =
    document.getElementById('qsgTotalCount');
const qsgHashCount =
    document.getElementById('qsgHashCount');
const qsgLangCount =
    document.getElementById('qsgLangCount');
const qsgFilteredCount =
    document.getElementById('qsgFilteredCount');
const replaceDarkSymbolTableBody =
    document.getElementById('replaceDarkSymbolTableBody');
const replaceDarkSymbolSummary =
    document.getElementById('replaceDarkSymbolSummary');
const replaceDarkSymbolRefresh =
    document.getElementById('replaceDarkSymbolRefresh');
const replaceSymbolTotalCount =
    document.getElementById('replaceSymbolTotalCount');
const replaceSymbolFromCount =
    document.getElementById('replaceSymbolFromCount');
const replaceSymbolToCount =
    document.getElementById('replaceSymbolToCount');
const replaceSymbolFilteredCount =
    document.getElementById('replaceSymbolFilteredCount');
const logTotalCount =
    document.getElementById('logTotalCount');
const logSuccessCount =
    document.getElementById('logSuccessCount');
const logFailedCount =
    document.getElementById('logFailedCount');
const logFilteredCount =
    document.getElementById('logFilteredCount');
const userTableBody =
    document.getElementById('userTableBody');
const userSummary =
    document.getElementById('userSummary');
const userRefresh =
    document.getElementById('userRefresh');
const userCreateForm =
    document.getElementById('userCreateForm');
const newUserId =
    document.getElementById('newUserId');
const newUserPassword =
    document.getElementById('newUserPassword');
const newUserName =
    document.getElementById('newUserName');
const newUserEmail =
    document.getElementById('newUserEmail');
const newUserRole =
    document.getElementById('newUserRole');
const newSlackUserId =
    document.getElementById('newSlackUserId');
const userTotalCount =
    document.getElementById('userTotalCount');
const userAdminCount =
    document.getElementById('userAdminCount');
const userNormalCount =
    document.getElementById('userNormalCount');
const userFilteredCount =
    document.getElementById('userFilteredCount');
let colorCheckDataTable = null;
let berAsisTobeDataTable = null;
// QSG DB 테이블의 DataTables 인스턴스. 다시 렌더링할 때 destroy 후 새로 만든다.
let qsgDbDataTable = null;
let replaceDarkSymbolDataTable = null;
let projectLogDataTable = null;
let userDataTable = null;

function profileImageUrl(version){
    return '/api/user/profile-image?userId='
        + encodeURIComponent(accountUserId)
        + (version ? '&v=' + version : '');
}

function refreshProfileImages(){
    const url = profileImageUrl(Date.now());

    [
        document.getElementById('accountMenuAvatarImage'),
        document.getElementById('accountProfilePreview')
    ].forEach(image => {
        image.hidden = false;
        image.src = url;
    });
}

function hideProfileImages(){
    document.getElementById(
        'accountMenuAvatarImage'
    ).hidden = true;
    document.getElementById(
        'accountProfilePreview'
    ).hidden = true;
}

function readErrorMessage(response, fallback){
    return response.text().then(text => {
        if(response.ok){
            return text ? JSON.parse(text) : {};
        }

        try{
            const body = JSON.parse(text);
            throw new Error(
                body.message || body.error || fallback
            );
        }catch(error){
            if(error instanceof SyntaxError){
                throw new Error(text || fallback);
            }
            throw error;
        }
    });
}

function openAccountDialog(){
    accountBackdrop.hidden = false;
    document.body.classList.add('dialog-open');
    document.getElementById('accountUserName').focus();
}

function closeAccountDialog(){
    accountBackdrop.hidden = true;
    document.body.classList.remove('dialog-open');
    document.getElementById('accountCurrentPassword').value = '';
    document.getElementById('accountNewPassword').value = '';
    document.getElementById('accountConfirmPassword').value = '';
    document.getElementById('accountFormError').textContent = '';
    updatePasswordMatchMessage();
}

function updatePasswordMatchMessage(){
    const newPasswordInput =
        document.getElementById('accountNewPassword');
    const confirmPasswordInput =
        document.getElementById('accountConfirmPassword');
    const messageElement =
        document.getElementById('accountPasswordMatchMessage');
    const saveButton =
        document.getElementById('accountSaveButton');
    const newPassword = newPasswordInput.value;
    const confirmPassword = confirmPasswordInput.value;
    const mismatched =
        (newPassword || confirmPassword)
        && newPassword !== confirmPassword;

    messageElement.textContent = mismatched
        ? '신규 비밀번호와 확인 비밀번호가 일치하지 않습니다.'
        : '';
    confirmPasswordInput.classList.toggle(
        'is-invalid',
        mismatched);
    saveButton.disabled = mismatched;

    return !mismatched;
}

function safeText(value){
    return value === null || value === undefined || value === ''
        ? '-'
        : String(value);
}

function formatDate(value){
    if(!value){
        return '-';
    }

    const date = new Date(value);

    if(Number.isNaN(date.getTime())){
        return String(value).replace('T', ' ');
    }

    const pad = number => String(number).padStart(2, '0');
    return date.getFullYear()
        + '-'
        + pad(date.getMonth() + 1)
        + '-'
        + pad(date.getDate())
        + ' '
        + pad(date.getHours())
        + ':'
        + pad(date.getMinutes());
}

function formatElapsed(value){
    if(value === null || value === undefined){
        return '-';
    }

    if(value < 1000){
        return value + 'ms';
    }

    return (value / 1000).toFixed(1) + 's';
}

function formatStatus(value){
    if(value === 'SUCCESS'){
        return '성공';
    }
    if(value === 'FAILED'){
        return '실패';
    }
    return safeText(value);
}

function formatRole(value){
    const role = safeText(value);
    const normalized = role.toUpperCase();

    if(normalized === 'ADMIN' || normalized === 'ROLE_ADMIN'){
        return '관리자';
    }
    if(normalized === 'Y' || normalized === 'USER' || normalized === 'ROLE_USER'){
        return 'Y';
    }
    return role;
}

function setImportResult(message, details){
    importResult.hidden = false;
    importResult.innerHTML = '';

    const summaryLine = document.createElement('div');
    summaryLine.className = 'admin-import-result-summary';
    summaryLine.textContent = message;
    importResult.appendChild(summaryLine);

    if(details && details.length > 0){
        const list = document.createElement('ul');
        details.slice(0, 5).forEach(detail => {
            const item = document.createElement('li');
            item.textContent = '행 '
                + detail.excelRowNumber
                + ' - '
                + safeText(detail.drawingName)
                + ': '
                + safeText(detail.note);
            list.appendChild(item);
        });

        if(details.length > 5){
            const item = document.createElement('li');
            item.textContent = '외 '
                + (details.length - 5).toLocaleString('ko-KR')
                + '건';
            list.appendChild(item);
        }

        importResult.appendChild(list);
    }
}

function setQsgDbImportResult(message){
    qsgDbImportResult.hidden = false;
    qsgDbImportResult.textContent = message;
}

function switchAdminView(view){
    const isColorCheckView = view === 'color-check';
    const isBerAsisTobeView = view === 'ber-asis-tobe';
    // 사이드바의 data-admin-view="qsg-db" 버튼과 연결되는 QSG DB 화면 분기.
    const isQsgDbView = view === 'qsg-db';
    const isReplaceDarkSymbolView = view === 'replace-dark-symbol';
    const isLogView = view === 'project-logs';
    const isUserView = view === 'users';
    colorCheckSection.hidden = !isColorCheckView;
    berAsisTobeSection.hidden = !isBerAsisTobeView;
    qsgDbSection.hidden = !isQsgDbView;
    replaceDarkSymbolSection.hidden = !isReplaceDarkSymbolView;
    projectLogSection.hidden = !isLogView;
    userSection.hidden = !isUserView;

    document.querySelectorAll('[data-admin-view]').forEach(button => {
        button.classList.toggle('active', button.dataset.adminView === view);
    });

    if(isColorCheckView && colorCheckState.items.length === 0){
        loadColorCheckItems();
    }
    if(isBerAsisTobeView && berAsisTobeState.items.length === 0){
        loadBerAsisTobeItems();
    }
    /*
     * QSG DB는 최초 진입 시 한 번만 XML을 읽어 온다.
     * 새로고침 버튼을 누르면 loadQsgDbItems()가 다시 호출되어 최신 리소스를 반영한다.
     */
    if(isQsgDbView && qsgDbState.items.length === 0){
        loadQsgDbItems();
    }
    if(isReplaceDarkSymbolView
            && replaceDarkSymbolState.items.length === 0){
        loadReplaceDarkSymbolItems();
    }
    if(isLogView && projectLogState.items.length === 0){
        loadProjectLogs();
    }
    if(isUserView && userState.items.length === 0){
        loadUsers();
    }
}

function dataTableLanguage(){
    return {
        lengthMenu: 'Show _MENU_ entries',
        search: 'Search:',
        info: 'Showing _START_ to _END_ of _TOTAL_ entries',
        infoEmpty: 'Showing 0 to 0 of 0 entries',
        infoFiltered: '(filtered from _MAX_ total entries)',
        zeroRecords: '표시할 데이터가 없습니다.',
        emptyTable: '표시할 데이터가 없습니다.',
        paginate: {
            previous: 'Previous',
            next: 'Next'
        }
    };
}

function destroyDataTable(instance){
    if(instance && typeof instance.destroy === 'function'){
        instance.destroy();
    }
}

function updateColorCheckFilteredCount(){
    if(!colorCheckDataTable){
        return;
    }
    const info = colorCheckDataTable.page.info();
    const total = colorCheckState.items.length;
    const showing = info.recordsDisplay;
    colorFilteredCount.textContent = showing.toLocaleString('ko-KR');
    summary.textContent = total === showing
        ? '총 ' + total.toLocaleString('ko-KR') + '건'
        : '총 ' + total.toLocaleString('ko-KR') + '건 중 '
            + showing.toLocaleString('ko-KR') + '건 표시';
}

function updateProjectLogFilteredCount(){
    if(!projectLogDataTable){
        return;
    }
    const info = projectLogDataTable.page.info();
    updateProjectLogSummary(info.recordsDisplay);
}

function updateBerAsisTobeFilteredCount(){
    if(!berAsisTobeDataTable){
        return;
    }
    const info = berAsisTobeDataTable.page.info();
    updateBerAsisTobeSummary(info.recordsDisplay);
}

function updateQsgDbFilteredCount(){
    if(!qsgDbDataTable){
        return;
    }
    const info = qsgDbDataTable.page.info();
    // DataTables 검색어가 적용된 후 화면에 남은 행 수를 상단 요약/카드에 반영한다.
    updateQsgDbSummary(info.recordsDisplay);
}

function updateReplaceDarkSymbolFilteredCount(){
    if(!replaceDarkSymbolDataTable){
        return;
    }
    const info = replaceDarkSymbolDataTable.page.info();
    updateReplaceDarkSymbolSummary(info.recordsDisplay);
}

function updateUserFilteredCount(){
    if(!userDataTable){
        return;
    }
    const info = userDataTable.page.info();
    updateUserSummary(info.recordsDisplay);
}

function initBerAsisTobeDataTable(){
    if(!window.jQuery || !jQuery.fn || !jQuery.fn.DataTable){
        updateBerAsisTobeSummary(berAsisTobeState.filteredItems.length);
        return;
    }
    /*
     * 관리자 > BER DB 테이블 초기화.
     *
     * 컬럼 순서:
     * 0 No, 1 Region, 2 Hash, 3 Old, 4 New, 5 수정일, 6 관리
     *
     * 최초 정렬:
     * - order [0, asc]라서 화면 진입 시 No 오름차순으로 보인다.
     *
     * 수정 시 주의:
     * - 컬럼을 추가/삭제하면 columnDefs targets, renderBerAsisTobeTable(),
     *   templates/admin/section/berAsisTobe.html의 thead 순서를 함께 수정해야 한다.
     */
    berAsisTobeDataTable = $('#berAsisTobeTable').DataTable({
        language: dataTableLanguage(),
        pageLength: 50,
        autoWidth: false,
        order: [ [0, 'asc'] ],
        columnDefs: [
            {targets: [0, 1, 5, 6], className: 'text-center'},
            {targets: [6], orderable: false, searchable: false},
            {targets: [0], width: '70px'},
            {targets: [1], width: '90px'},
            {targets: [2], width: '520px'},
            {targets: [3, 4], width: '360px'},
            {targets: [5], width: '140px'},
            {targets: [6], width: '90px'}
        ],
        drawCallback: updateBerAsisTobeFilteredCount
    });
    updateBerAsisTobeFilteredCount();
}

function initQsgDbDataTable(){
    if(!window.jQuery || !jQuery.fn || !jQuery.fn.DataTable){
        updateQsgDbSummary(qsgDbState.filteredItems.length);
        return;
    }
    /*
     * 관리자 > QSG DB 테이블 초기화.
     *
     * 컬럼 순서:
     * 0 No, 1 Hash, 2 Lang, 3 Term
     *
     * 최초 정렬:
     * - order [0, asc], [1, asc], [2, asc]라서 화면 진입 시
     *   No -> Hash -> Lang 오름차순으로 보인다.
     *
     * 수정 시 주의:
     * - Hash와 Term은 긴 문자열이라 width를 고정해 표가 크게 흔들리지 않게 했다.
     * - 컬럼을 추가/삭제하면 columnDefs targets, renderQsgDbTable(),
     *   templates/admin/section/qsgDb.html의 thead 순서를 함께 수정해야 한다.
     */
    qsgDbDataTable = $('#qsgDbTable').DataTable({
        language: dataTableLanguage(),
        pageLength: 50,
        autoWidth: false,
        order: [ [0, 'asc'], [1, 'asc'], [2, 'asc'] ],
        columnDefs: [
            {targets: [0, 2], className: 'text-center'},
            {targets: [0], width: '70px'},
            {targets: [1], width: '520px'},
            {targets: [2], width: '100px'},
            {targets: [3], width: '620px'}
        ],
        drawCallback: updateQsgDbFilteredCount
    });
    updateQsgDbFilteredCount();
}

function initReplaceDarkSymbolDataTable(){
    if(!window.jQuery || !jQuery.fn || !jQuery.fn.DataTable){
        updateReplaceDarkSymbolSummary(
                replaceDarkSymbolState.filteredItems.length);
        return;
    }

    /*
     * Replace Symbol DB 테이블 초기화.
     *
     * 컬럼 순서:
     * 0 No, 1 From, 2 To, 3 수정일, 4 관리
     *
     * From/To는 긴 문장이 아니라 이미지 코드라서 BER Old/New보다 좁고 가운데 정렬한다.
     */
    replaceDarkSymbolDataTable = $('#replaceDarkSymbolTable').DataTable({
        language: dataTableLanguage(),
        pageLength: 50,
        autoWidth: false,
        order: [ [0, 'asc'] ],
        columnDefs: [
            {targets: [0, 1, 2, 3, 4], className: 'text-center'},
            {targets: [4], orderable: false, searchable: false},
            {targets: [0], width: '70px'},
            {targets: [1], width: '260px'},
            {targets: [2], width: '180px'},
            {targets: [3], width: '140px'},
            {targets: [4], width: '90px'}
        ],
        drawCallback: updateReplaceDarkSymbolFilteredCount
    });
    updateReplaceDarkSymbolFilteredCount();
}

function initColorCheckDataTable(){
    if(!window.jQuery || !jQuery.fn || !jQuery.fn.DataTable){
        updateColorCheckFilteredCount();
        return;
    }
    colorCheckDataTable = $('#colorCheckTable').DataTable({
        language: dataTableLanguage(),
        pageLength: 50,
        order: [ [3, 'desc'] ],
        columnDefs: [
            {targets: [0, 2, 4], className: 'text-center'},
            {targets: [4], orderable: false, searchable: false}
        ],
        drawCallback: updateColorCheckFilteredCount
    });
    updateColorCheckFilteredCount();
}

function initProjectLogDataTable(){
    if(!window.jQuery || !jQuery.fn || !jQuery.fn.DataTable){
        updateProjectLogSummary(projectLogState.filteredItems.length);
        return;
    }
    projectLogDataTable = $('#projectLogTable').DataTable({
        language: dataTableLanguage(),
        pageLength: 50,
        order: [ [4, 'desc'] ],
        columnDefs: [
            {targets: [0, 1, 3, 5], className: 'text-center'}
        ],
        drawCallback: updateProjectLogFilteredCount
    });
    updateProjectLogFilteredCount();
}

function initUserDataTable(){
    if(!window.jQuery || !jQuery.fn || !jQuery.fn.DataTable){
        updateUserSummary(userState.filteredItems.length);
        return;
    }
    userDataTable = $('#userTable').DataTable({
        language: dataTableLanguage(),
        pageLength: 50,
        order: [ [0, 'asc'] ],
        columnDefs: [
            {targets: [1, 4, 6], className: 'text-center'},
            {targets: [0], width: '16%'},
            {targets: [1], width: '12%'},
            {targets: [2], width: '16%'},
            {targets: [3], width: '22%'},
            {targets: [4], width: '10%'},
            {targets: [5], width: '14%'},
            {targets: [6], width: '10%', orderable: false, searchable: false}
        ],
        drawCallback: updateUserFilteredCount
    });
    updateUserFilteredCount();
}

function renderTable(){
    destroyDataTable(colorCheckDataTable);
    colorCheckDataTable = null;
    tableBody.innerHTML = '';

    colorCheckState.filteredItems.forEach((item, index) => {
        const row = document.createElement('tr');
        const values = [
            index + 1,
            safeText(item.drawingName),
            safeText(item.checkValue),
            formatDate(item.regDt),
            ''
        ];

        values.forEach((value, columnIndex) => {
            const cell = document.createElement('td');

            if(columnIndex === 2){
                cell.className = 'color-check-value-cell';
                cell.textContent = value;
            }else if(columnIndex === 4){
                const actions = document.createElement('div');
                actions.className = 'admin-row-actions';
                const editButton = document.createElement('button');
                editButton.className = 'admin-icon-btn';
                editButton.type = 'button';
                editButton.title = '수정';
                editButton.innerHTML = '<i class="fas fa-pen"></i>';
                editButton.addEventListener('click', () => startEdit(item));
                const deleteButton = document.createElement('button');
                deleteButton.className = 'admin-icon-btn danger';
                deleteButton.type = 'button';
                deleteButton.title = '삭제';
                deleteButton.innerHTML = '<i class="fas fa-trash"></i>';
                deleteButton.addEventListener('click', () => deleteItem(item));
                actions.appendChild(editButton);
                actions.appendChild(deleteButton);
                cell.appendChild(actions);
            }else{
                cell.textContent = value;
            }

            row.appendChild(cell);
        });

        tableBody.appendChild(row);
    });
    initColorCheckDataTable();
}

function compareText(left, right){
    return String(left || '').localeCompare(
        String(right || ''),
        'ko',
        {numeric:true, sensitivity:'base'});
}

function sortItems(items){
    const direction = colorCheckState.sortDirection === 'asc' ? 1 : -1;
    const key = colorCheckState.sortKey;

    return [...items].sort((left, right) => {
        let result;

        if(key === 'regDt'){
            result = new Date(left.regDt || 0) - new Date(right.regDt || 0);
        }else{
            result = compareText(left[key], right[key]);
        }

        return result * direction;
    });
}

function updateSortIcons(){
    document.querySelectorAll('[data-sort-icon]').forEach(icon => {
        const key = icon.dataset.sortIcon;
        icon.textContent = key === colorCheckState.sortKey
            ? (colorCheckState.sortDirection === 'asc' ? '▲' : '▼')
            : '';
    });
}

function updateSummary(){
    const total = colorCheckState.items.length;
    const showing = colorCheckState.filteredItems.length;
    const checked = colorCheckState.items
        .filter(item => item.checkValue === 'V').length;
    const unchecked = colorCheckState.items
        .filter(item => item.checkValue === 'X').length;

    summary.textContent = total === showing
        ? '총 ' + total.toLocaleString('ko-KR') + '건'
        : '총 ' + total.toLocaleString('ko-KR') + '건 중 '
            + showing.toLocaleString('ko-KR') + '건 표시';
    colorTotalCount.textContent = total.toLocaleString('ko-KR');
    colorCheckedCount.textContent = checked.toLocaleString('ko-KR');
    colorUncheckedCount.textContent = unchecked.toLocaleString('ko-KR');
    colorFilteredCount.textContent = showing.toLocaleString('ko-KR');
}

function applyFilter(){
    colorCheckState.filteredItems = sortItems(colorCheckState.items);

    updateSummary();
    renderTable();
}

function resetForm(){
    colorCheckState.editingName = '';
    drawingNameInput.value = '';
    drawingNameInput.disabled = false;
    checkValueSelect.value = 'V';
    cancelButton.hidden = true;
}

function startEdit(item){
    colorCheckState.editingName = item.drawingName || '';
    drawingNameInput.value = item.drawingName || '';
    drawingNameInput.disabled = true;
    checkValueSelect.value = item.checkValue === 'X' ? 'X' : 'V';
    cancelButton.hidden = false;
    checkValueSelect.focus();
}

async function saveItem(event){
    event.preventDefault();
    const drawingName = drawingNameInput.value.trim();
    const checkValue = checkValueSelect.value;

    if(!drawingName){
        summary.textContent = '도안명을 입력해 주세요.';
        drawingNameInput.focus();
        return;
    }

    try{
        const response = await fetch('/admin/color-check/items', {
            method:'PUT',
            headers:{
                'Content-Type':'application/json',
                'Accept':'application/json'
            },
            body:JSON.stringify({
                drawingName:drawingName,
                checkValue:checkValue
            })
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        resetForm();
        await loadColorCheckItems();
    }catch(error){
        summary.textContent = error.message || '저장하지 못했습니다.';
    }
}

async function deleteItem(item){
    const drawingName = item.drawingName || '';

    if(!drawingName || !confirm(drawingName + ' 항목을 삭제할까요?')){
        return;
    }

    try{
        const response = await fetch(
            '/admin/color-check/items/' + encodeURIComponent(drawingName),
            {method:'DELETE'});

        if(!response.ok){
            throw new Error(await response.text());
        }

        if(colorCheckState.editingName === drawingName){
            resetForm();
        }

        await loadColorCheckItems();
    }catch(error){
        summary.textContent = error.message || '삭제하지 못했습니다.';
    }
}

function changeSort(key){
    if(colorCheckState.sortKey === key){
        colorCheckState.sortDirection =
            colorCheckState.sortDirection === 'asc' ? 'desc' : 'asc';
    }else{
        colorCheckState.sortKey = key;
        colorCheckState.sortDirection = key === 'regDt' ? 'desc' : 'asc';
    }

    applyFilter();
}

async function loadColorCheckItems(){
    refreshButton.disabled = true;
    summary.textContent = '데이터를 불러오는 중입니다.';

    try{
        const response = await fetch('/admin/color-check/items', {
            headers: {
                'Accept': 'application/json'
            }
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        colorCheckState.items = await response.json();
        applyFilter();
    }catch(error){
        tableBody.innerHTML = '';
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 5;
        cell.className = 'admin-empty-cell';
        cell.textContent = '견적 DB를 불러오지 못했습니다.';
        row.appendChild(cell);
        tableBody.appendChild(row);
        summary.textContent = error.message || '조회 중 오류가 발생했습니다.';
    }finally{
        refreshButton.disabled = false;
    }
}

function renderBerAsisTobeTable(){
    destroyDataTable(berAsisTobeDataTable);
    berAsisTobeDataTable = null;
    berAsisTobeTableBody.innerHTML = '';

    berAsisTobeState.filteredItems.forEach((item, index) => {
        const row = document.createElement('tr');
        const values = [
            index + 1,
            safeText(item.region),
            safeText(item.hash),
            safeText(item.oldText),
            safeText(item.newText),
            formatDate(item.updatedAt),
            ''
        ];

        values.forEach((value, columnIndex) => {
            const cell = document.createElement('td');

            if(columnIndex === 1){
                cell.className = 'ber-region-cell';
                cell.textContent = value;
            }else if(columnIndex === 2){
                cell.className = 'ber-hash-cell';
                cell.textContent = value;
                if(value !== '-'){
                    cell.title = value;
                }
            }else if(columnIndex === 3 || columnIndex === 4){
                cell.className = 'ber-long-text-cell';
                const textBox = document.createElement('div');
                textBox.className = 'ber-clamped-text';
                textBox.textContent = value;
                cell.appendChild(textBox);
                if(value !== '-'){
                    cell.title = value;
                }
                cell.classList.add('admin-editable-cell');
                cell.title = value === '-'
                    ? '더블클릭해서 수정'
                    : value + '\n\n더블클릭해서 수정';
                cell.addEventListener('dblclick', () => {
                    startBerAsisTobeCellEdit(
                            cell,
                            item,
                            columnIndex === 3 ? 'oldText' : 'newText');
                });
            }else if(columnIndex === 6){
                const actions = document.createElement('div');
                actions.className = 'admin-row-actions';
                const deleteButton = document.createElement('button');
                deleteButton.className = 'admin-icon-btn danger';
                deleteButton.type = 'button';
                deleteButton.title = '삭제';
                deleteButton.innerHTML = '<i class="fas fa-trash"></i>';
                deleteButton.addEventListener(
                    'click',
                    () => deleteBerAsisTobeItem(item));
                actions.appendChild(deleteButton);
                cell.appendChild(actions);
            }else{
                cell.textContent = value;
            }

            row.appendChild(cell);
        });

        berAsisTobeTableBody.appendChild(row);
    });
    initBerAsisTobeDataTable();
}

function startBerAsisTobeCellEdit(cell, item, field){
    if(berAsisTobeState.editingCell){
        return;
    }

    const originalValue = item[field] || '';
    berAsisTobeState.editingCell = cell;
    cell.innerHTML = '';
    cell.title = '';

    const editor = document.createElement('textarea');
    editor.className = 'admin-inline-editor ber-inline-editor';
    editor.value = originalValue;

    let closed = false;
    const finish = async shouldSave => {
        if(closed){
            return;
        }
        closed = true;
        const value = editor.value;

        if(!shouldSave || value === originalValue){
            restoreBerAsisTobeCell(cell, originalValue);
            berAsisTobeState.editingCell = null;
            return;
        }

        try{
            const updatedItem = await saveBerAsisTobeField(item, field, value);
            Object.assign(item, updatedItem);
            restoreBerAsisTobeCell(
                    cell,
                    updatedItem[field] || '');
        }catch(error){
            restoreBerAsisTobeCell(cell, originalValue);
            berAsisTobeSummary.textContent =
                    error.message || 'BER asis-tobe 항목을 저장하지 못했습니다.';
        }finally{
            berAsisTobeState.editingCell = null;
        }
    };

    editor.addEventListener('keydown', event => {
        if(event.key === 'Escape'){
            event.preventDefault();
            finish(false);
        }
        if((event.ctrlKey || event.metaKey) && event.key === 'Enter'){
            event.preventDefault();
            editor.blur();
        }
    });
    editor.addEventListener('blur', () => finish(true));
    cell.appendChild(editor);
    editor.focus();
    editor.select();
}

function restoreBerAsisTobeCell(cell, value){
    cell.innerHTML = '';
    const textBox = document.createElement('div');
    textBox.className = 'ber-clamped-text';
    const displayValue = value || '-';
    textBox.textContent = displayValue;
    cell.appendChild(textBox);
    cell.title = displayValue === '-'
        ? '더블클릭해서 수정'
        : displayValue + '\n\n더블클릭해서 수정';
}

async function saveBerAsisTobeField(item, field, value){
    const payload = {
        region:item.region,
        hash:item.hash,
        oldText:item.oldText || '',
        newText:item.newText || ''
    };
    payload[field] = value;

    const response = await fetch('/admin/ber-asis-tobe/items', {
        method:'PUT',
        headers:{
            'Content-Type':'application/json',
            'Accept':'application/json'
        },
        body:JSON.stringify(payload)
    });

    if(!response.ok){
        throw new Error(await response.text());
    }

    return response.json();
}

function updateBerAsisTobeSummary(showing){
    const total = berAsisTobeState.items.length;
    const euCount = berAsisTobeState.items
        .filter(item => item.region === 'EU').length;
    const euRgCount = berAsisTobeState.items
        .filter(item => item.region === 'EU_RG').length;
    const usCount = berAsisTobeState.items
        .filter(item => item.region === 'US').length;

    berAsisTobeSummary.textContent = total === showing
        ? '총 ' + total.toLocaleString('ko-KR') + '건'
        : '총 ' + total.toLocaleString('ko-KR') + '건 중 '
            + showing.toLocaleString('ko-KR') + '건 표시';
    berTotalCount.textContent = total.toLocaleString('ko-KR');
    berEuCount.textContent = euCount.toLocaleString('ko-KR');
    berEuRgCount.textContent = euRgCount.toLocaleString('ko-KR');
    berUsCount.textContent = usCount.toLocaleString('ko-KR');
    berFilteredCount.textContent = showing.toLocaleString('ko-KR');
}

function applyBerAsisTobeFilter(){
    berAsisTobeState.filteredItems = [...berAsisTobeState.items];
    updateBerAsisTobeSummary(berAsisTobeState.filteredItems.length);
    renderBerAsisTobeTable();
}

function renderQsgDbTable(){
    destroyDataTable(qsgDbDataTable);
    qsgDbDataTable = null;
    qsgDbTableBody.innerHTML = '';

    /*
     * API 한 행은 QsgDbTerm DTO와 같은 구조다.
     * hash + lang이 실제 유니크 기준이고, No는 현재 표시 순서만 보여준다.
     */
    qsgDbState.filteredItems.forEach((item, index) => {
        const row = document.createElement('tr');
        const values = [
            index + 1,
            safeText(item.hash),
            safeText(item.lang),
            safeText(item.term)
        ];

        values.forEach((value, columnIndex) => {
            const cell = document.createElement('td');

            if(columnIndex === 1){
                cell.className = 'ber-hash-cell';
                cell.textContent = value;
                if(value !== '-'){
                    cell.title = value;
                }
            }else if(columnIndex === 3){
                cell.className = 'ber-long-text-cell';
                const textBox = document.createElement('div');
                textBox.className = 'ber-clamped-text';
                textBox.textContent = value;
                cell.appendChild(textBox);
                if(value !== '-'){
                    cell.title = value;
                }
            }else{
                cell.textContent = value;
            }

            row.appendChild(cell);
        });

        qsgDbTableBody.appendChild(row);
    });
    initQsgDbDataTable();
}

function updateQsgDbSummary(showing){
    const total = qsgDbState.items.length;
    // hashCount는 원본 QSG_DB.xml의 entry 개수와 같은 의미다.
    const hashCount = new Set(
        qsgDbState.items.map(item => item.hash).filter(Boolean)
    ).size;
    // langCount는 현재 QSG_DB.xml에 들어 있는 전체 언어 코드 종류 수다.
    const langCount = new Set(
        qsgDbState.items.map(item => item.lang).filter(Boolean)
    ).size;

    qsgDbSummary.textContent = total === showing
        ? '총 ' + total.toLocaleString('ko-KR') + '건'
        : '총 ' + total.toLocaleString('ko-KR') + '건 중 '
            + showing.toLocaleString('ko-KR') + '건 표시';
    qsgTotalCount.textContent = total.toLocaleString('ko-KR');
    qsgHashCount.textContent = hashCount.toLocaleString('ko-KR');
    qsgLangCount.textContent = langCount.toLocaleString('ko-KR');
    qsgFilteredCount.textContent = showing.toLocaleString('ko-KR');
}

function applyQsgDbFilter(){
    // 별도 필터 UI는 아직 없고, 검색/정렬은 DataTables 기본 기능에 맡긴다.
    qsgDbState.filteredItems = [...qsgDbState.items];
    updateQsgDbSummary(qsgDbState.filteredItems.length);
    renderQsgDbTable();
}

function renderReplaceDarkSymbolTable(){
    destroyDataTable(replaceDarkSymbolDataTable);
    replaceDarkSymbolDataTable = null;
    replaceDarkSymbolTableBody.innerHTML = '';

    /*
     * item.fromSymbol은 XML의 replace/@from, item.toSymbol은 replace/@to다.
     * 표시는 코드 전용 셀 클래스를 써서 BER의 긴 문장 셀 스타일과 분리한다.
     */
    replaceDarkSymbolState.filteredItems.forEach((item, index) => {
        const row = document.createElement('tr');
        const values = [
            index + 1,
            safeText(item.fromSymbol),
            safeText(item.toSymbol),
            formatDate(item.updatedAt),
            ''
        ];

        values.forEach((value, columnIndex) => {
            const cell = document.createElement('td');

            if(columnIndex === 1 || columnIndex === 2){
                cell.className = 'replace-symbol-code-cell';
                const field = columnIndex === 1 ? 'fromSymbol' : 'toSymbol';
                const textBox = document.createElement('div');
                textBox.className = 'replace-symbol-code-text';
                textBox.textContent = value;
                cell.appendChild(textBox);
                cell.title = value === '-'
                    ? '더블클릭해서 수정'
                    : value + '\n\n더블클릭해서 수정';
                cell.addEventListener('dblclick', () => {
                    startReplaceDarkSymbolCellEdit(cell, item, field);
                });
            }else if(columnIndex === 4){
                const actions = document.createElement('div');
                actions.className = 'admin-row-actions';
                const deleteButton = document.createElement('button');
                deleteButton.className = 'admin-icon-btn danger';
                deleteButton.type = 'button';
                deleteButton.title = '삭제';
                deleteButton.innerHTML = '<i class="fas fa-trash"></i>';
                deleteButton.addEventListener(
                    'click',
                    () => deleteReplaceDarkSymbolItem(item));
                actions.appendChild(deleteButton);
                cell.appendChild(actions);
            }else{
                cell.textContent = value;
            }

            row.appendChild(cell);
        });

        replaceDarkSymbolTableBody.appendChild(row);
    });
    initReplaceDarkSymbolDataTable();
}

function startReplaceDarkSymbolCellEdit(cell, item, field){
    if(replaceDarkSymbolState.editingCell){
        return;
    }

    const originalValue = item[field] || '';
    replaceDarkSymbolState.editingCell = cell;
    cell.innerHTML = '';
    cell.title = '';

    const editor = document.createElement('input');
    editor.className = 'admin-inline-editor';
    editor.type = 'text';
    editor.value = originalValue;

    let closed = false;
    const finish = async shouldSave => {
        if(closed){
            return;
        }
        closed = true;
        const value = editor.value;

        if(!shouldSave || value === originalValue){
            restoreReplaceDarkSymbolCell(cell, originalValue);
            replaceDarkSymbolState.editingCell = null;
            return;
        }

        try{
            const updatedItem =
                await saveReplaceDarkSymbolField(item, field, value);
            Object.assign(item, updatedItem);
            restoreReplaceDarkSymbolCell(
                    cell,
                    updatedItem[field] || '');
        }catch(error){
            restoreReplaceDarkSymbolCell(cell, originalValue);
            replaceDarkSymbolSummary.textContent =
                    error.message || 'Replace Symbol 항목을 저장하지 못했습니다.';
        }finally{
            replaceDarkSymbolState.editingCell = null;
        }
    };

    editor.addEventListener('keydown', event => {
        if(event.key === 'Escape'){
            event.preventDefault();
            finish(false);
        }
        if((event.ctrlKey || event.metaKey) && event.key === 'Enter'){
            event.preventDefault();
            editor.blur();
        }
    });
    editor.addEventListener('blur', () => finish(true));
    cell.appendChild(editor);
    editor.focus();
    editor.select();
}

function restoreReplaceDarkSymbolCell(cell, value){
    cell.innerHTML = '';
    const textBox = document.createElement('div');
    textBox.className = 'replace-symbol-code-text';
    const displayValue = value || '-';
    textBox.textContent = displayValue;
    cell.appendChild(textBox);
    cell.title = displayValue === '-'
        ? '더블클릭해서 수정'
        : displayValue + '\n\n더블클릭해서 수정';
}

async function saveReplaceDarkSymbolField(item, field, value){
    /*
     * From 자체를 수정할 수도 있으므로 현재 행의 두 값을 모두 보내고,
     * 서버는 새 fromSymbol 기준으로 upsert한다.
     */
    const payload = {
        fromSymbol:item.fromSymbol || '',
        toSymbol:item.toSymbol || ''
    };
    payload[field] = value;

    const response = await fetch('/admin/replace-dark-symbol/items', {
        method:'PUT',
        headers:{
            'Content-Type':'application/json',
            'Accept':'application/json'
        },
        body:JSON.stringify(payload)
    });

    if(!response.ok){
        throw new Error(await response.text());
    }

    return response.json();
}

function updateReplaceDarkSymbolSummary(showing){
    const total = replaceDarkSymbolState.items.length;
    // 치환 대상은 중복 제거한 From 개수다.
    const fromCount = new Set(
        replaceDarkSymbolState.items
            .map(item => item.fromSymbol)
            .filter(Boolean)
    ).size;
    // 치환 결과는 중복 제거한 To 개수다. 여러 From이 같은 To로 모일 수 있다.
    const toCount = new Set(
        replaceDarkSymbolState.items
            .map(item => item.toSymbol)
            .filter(Boolean)
    ).size;

    replaceDarkSymbolSummary.textContent = total === showing
        ? '총 ' + total.toLocaleString('ko-KR') + '건'
        : '총 ' + total.toLocaleString('ko-KR') + '건 중 '
            + showing.toLocaleString('ko-KR') + '건 표시';
    replaceSymbolTotalCount.textContent = total.toLocaleString('ko-KR');
    replaceSymbolFromCount.textContent = fromCount.toLocaleString('ko-KR');
    replaceSymbolToCount.textContent = toCount.toLocaleString('ko-KR');
    replaceSymbolFilteredCount.textContent = showing.toLocaleString('ko-KR');
}

function applyReplaceDarkSymbolFilter(){
    replaceDarkSymbolState.filteredItems = [...replaceDarkSymbolState.items];
    updateReplaceDarkSymbolSummary(
            replaceDarkSymbolState.filteredItems.length);
    renderReplaceDarkSymbolTable();
}

async function loadReplaceDarkSymbolItems(){
    replaceDarkSymbolRefresh.disabled = true;
    replaceDarkSymbolSummary.textContent = '데이터를 불러오는 중입니다.';

    try{
        // 관리자 화면은 DB 원본을 조회한다. XML 파일은 실행 직전에만 다시 만든다.
        const response = await fetch('/admin/replace-dark-symbol/items', {
            headers:{
                'Accept':'application/json'
            }
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        replaceDarkSymbolState.items = await response.json();
        applyReplaceDarkSymbolFilter();
    }catch(error){
        replaceDarkSymbolTableBody.innerHTML = '';
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 5;
        cell.className = 'admin-empty-cell';
        cell.textContent = 'Replace Symbol DB를 불러오지 못했습니다.';
        row.appendChild(cell);
        replaceDarkSymbolTableBody.appendChild(row);
        replaceDarkSymbolSummary.textContent =
            error.message || '조회 중 오류가 발생했습니다.';
    }finally{
        replaceDarkSymbolRefresh.disabled = false;
    }
}

async function deleteReplaceDarkSymbolItem(item){
    const fromSymbol = item.fromSymbol || '';

    if(!fromSymbol || !confirm(fromSymbol + ' 항목을 삭제할까요?')){
        return;
    }

    try{
        const response = await fetch(
            '/admin/replace-dark-symbol/items/'
                + encodeURIComponent(fromSymbol),
            {method:'DELETE'});

        if(!response.ok){
            throw new Error(await response.text());
        }

        await loadReplaceDarkSymbolItems();
    }catch(error){
        replaceDarkSymbolSummary.textContent =
            error.message || '삭제하지 못했습니다.';
    }
}

async function loadQsgDbItems(){
    qsgDbRefresh.disabled = true;
    qsgDbSummary.textContent = 'QSG DB를 불러오는 중입니다.';

    try{
        /*
         * 백엔드는 src/main/resources/xsl/QSG_DB.xml을 읽어서 내려준다.
         * 나중에 실제 DB 테이블로 바꿔도 이 URL을 유지하면 프론트 수정이 작다.
         */
        const response = await fetch('/admin/qsg-db/items', {
            headers:{
                'Accept':'application/json'
            }
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        qsgDbState.items = await response.json();
        applyQsgDbFilter();
    }catch(error){
        qsgDbTableBody.innerHTML = '';
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 4;
        cell.className = 'admin-empty-cell';
        cell.textContent = 'QSG DB를 불러오지 못했습니다.';
        row.appendChild(cell);
        qsgDbTableBody.appendChild(row);
        qsgDbSummary.textContent =
            error.message || '조회 중 오류가 발생했습니다.';
    }finally{
        qsgDbRefresh.disabled = false;
    }
}

async function importQsgDbExcel(event){
    if(event){
        event.preventDefault();
    }

    const file = qsgDbImportFile.files && qsgDbImportFile.files[0];
    if(!file){
        setQsgDbImportResult('업로드할 엑셀 파일을 선택해 주세요.');
        qsgDbImportFile.focus();
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    qsgDbImportButton.disabled = true;
    setQsgDbImportResult('QSG DB 엑셀을 업로드하는 중입니다.');

    try{
        const response = await fetch('/admin/qsg-db/import', {
            method:'POST',
            headers:{
                'Accept':'application/json'
            },
            body:formData
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        const result = await response.json();
        setQsgDbImportResult(
            '업로드 완료: 신규 '
                + result.insertedCount.toLocaleString('ko-KR')
                + '건, 수정 '
                + result.updatedCount.toLocaleString('ko-KR')
                + '건, 변경 없음 '
                + result.unchangedCount.toLocaleString('ko-KR')
                + '건, 제외 '
                + result.skippedCount.toLocaleString('ko-KR')
                + '건');
        qsgDbImportFile.value = '';
        await loadQsgDbItems();
    }catch(error){
        setQsgDbImportResult(
            error.message || 'QSG DB 엑셀 업로드에 실패했습니다.');
    }finally{
        qsgDbImportButton.disabled = false;
    }
}

async function loadBerAsisTobeItems(){
    berAsisTobeRefresh.disabled = true;
    berAsisTobeSummary.textContent = '데이터를 불러오는 중입니다.';

    try{
        const response = await fetch('/admin/ber-asis-tobe/items', {
            headers:{
                'Accept':'application/json'
            }
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        berAsisTobeState.items = await response.json();
        applyBerAsisTobeFilter();
    }catch(error){
        berAsisTobeTableBody.innerHTML = '';
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 7;
        cell.className = 'admin-empty-cell';
        cell.textContent = 'BER asis-tobe DB를 불러오지 못했습니다.';
        row.appendChild(cell);
        berAsisTobeTableBody.appendChild(row);
        berAsisTobeSummary.textContent =
            error.message || '조회 중 오류가 발생했습니다.';
    }finally{
        berAsisTobeRefresh.disabled = false;
    }
}

async function deleteBerAsisTobeItem(item){
    const region = item.region || '';
    const hash = item.hash || '';

    if(!region
            || !hash
            || !confirm(region + ' / ' + hash + ' 항목을 삭제할까요?')){
        return;
    }

    try{
        const response = await fetch(
            '/admin/ber-asis-tobe/items/'
                + encodeURIComponent(region)
                + '/'
                + encodeURIComponent(hash),
            {method:'DELETE'});

        if(!response.ok){
            throw new Error(await response.text());
        }

        await loadBerAsisTobeItems();
    }catch(error){
        berAsisTobeSummary.textContent =
            error.message || '삭제하지 못했습니다.';
    }
}

function setBerAsisTobeImportResult(message, details){
    berAsisTobeImportResult.hidden = false;
    berAsisTobeImportResult.innerHTML = '';

    const summaryLine = document.createElement('div');
    summaryLine.className = 'admin-import-result-summary';
    summaryLine.textContent = message;
    berAsisTobeImportResult.appendChild(summaryLine);

    if(details && details.length > 0){
        const list = document.createElement('ul');
        details.slice(0, 5).forEach(detail => {
            const item = document.createElement('li');
            item.textContent = '행 '
                + detail.excelRowNumber
                + ' - '
                + safeText(detail.region)
                + ' / '
                + safeText(detail.hash)
                + ': '
                + safeText(detail.note || detail.status);
            list.appendChild(item);
        });

        if(details.length > 5){
            const item = document.createElement('li');
            item.textContent = '외 '
                + (details.length - 5).toLocaleString('ko-KR')
                + '건';
            list.appendChild(item);
        }

        berAsisTobeImportResult.appendChild(list);
    }
}

async function importBerAsisTobeExcel(event){
    if(event){
        event.preventDefault();
    }

    const file = berAsisTobeImportFile.files
        && berAsisTobeImportFile.files[0];
    if(!file){
        setBerAsisTobeImportResult('업로드할 엑셀 파일을 선택해 주세요.');
        berAsisTobeImportFile.focus();
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    berAsisTobeImportButton.disabled = true;
    setBerAsisTobeImportResult('엑셀을 업로드하는 중입니다.');

    try{
        const response = await fetch('/admin/ber-asis-tobe/import', {
            method:'POST',
            headers:{
                'Accept':'application/json'
            },
            body:formData
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        const result = await response.json();
        const skippedDetails = (result.details || [])
            .filter(detail => detail.status === '제외');
        setBerAsisTobeImportResult(
            '업로드 완료: 신규 '
                + result.insertedCount.toLocaleString('ko-KR')
                + '건, 수정 '
                + result.updatedCount.toLocaleString('ko-KR')
                + '건, 변경 없음 '
                + result.unchangedCount.toLocaleString('ko-KR')
                + '건, 제외 '
                + result.skippedCount.toLocaleString('ko-KR')
                + '건',
            skippedDetails);
        berAsisTobeImportFile.value = '';
        await loadBerAsisTobeItems();
    }catch(error){
        setBerAsisTobeImportResult(
            error.message || '엑셀 업로드에 실패했습니다.');
    }finally{
        berAsisTobeImportButton.disabled = false;
    }
}

function openBerSentenceImportPopup(){
    const popup = window.open(
        '/admin/ber-asis-tobe/sentence-import-popup',
        'berSentenceImportPopup',
        'width=680,height=500'
    );

    if(popup){
        popup.focus();
    }
}

function renderProjectLogs(){
    destroyDataTable(projectLogDataTable);
    projectLogDataTable = null;
    projectLogTableBody.innerHTML = '';

    projectLogState.filteredItems.forEach((item, index) => {
        const row = document.createElement('tr');
        const values = [
            index + 1,
            formatStatus(item.status),
            item.jobName || item.jobType,
            item.triggerUserId || '-',
            formatDate(item.startedAt),
            formatElapsed(item.elapsedMs),
            [
                item.inputPath || '',
                item.outputPath || ''
            ].filter(Boolean).join(' -> ') || '-',
            item.status === 'FAILED'
                ? (item.errorMessage || item.message || '-')
                : (item.message || '-')
        ];

        values.forEach((value, columnIndex) => {
            const cell = document.createElement('td');
            cell.textContent = safeText(value);

            row.appendChild(cell);
        });

        projectLogTableBody.appendChild(row);
    });
    initProjectLogDataTable();
}

function applyProjectLogFilter(){
    projectLogState.filteredItems = [...projectLogState.items];
    updateProjectLogSummary(projectLogState.filteredItems.length);
    renderProjectLogs();
}

function updateProjectLogSummary(showing){
    const total = projectLogState.items.length;
    const success = projectLogState.items
        .filter(item => item.status === 'SUCCESS').length;
    const failed = projectLogState.items
        .filter(item => item.status === 'FAILED').length;

    projectLogSummary.textContent = '전체 '
        + total.toLocaleString('ko-KR')
        + '건 중 '
        + showing.toLocaleString('ko-KR')
        + '건 표시';
    logTotalCount.textContent = total.toLocaleString('ko-KR');
    logSuccessCount.textContent = success.toLocaleString('ko-KR');
    logFailedCount.textContent = failed.toLocaleString('ko-KR');
    logFilteredCount.textContent = showing.toLocaleString('ko-KR');
}

async function loadProjectLogs(){
    projectLogSummary.textContent = '전체 실행 이력을 불러오는 중입니다.';

    try{
        const response = await fetch('/admin/project-logs', {
            headers:{
                'Accept':'application/json'
            }
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        projectLogState.items = await response.json();
        applyProjectLogFilter();
    }catch(error){
        projectLogTableBody.innerHTML = '';
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 8;
        cell.className = 'admin-empty-cell';
        cell.textContent = '실행 로그를 불러오지 못했습니다.';
        row.appendChild(cell);
        projectLogTableBody.appendChild(row);
        projectLogSummary.textContent = error.message || '조회 중 오류가 발생했습니다.';
    }
}

async function importColorCheckExcel(event){
    if(event){
        event.preventDefault();
    }

    const file = importFileInput.files && importFileInput.files[0];
    if(!file){
        setImportResult('업로드할 엑셀 파일을 선택해 주세요.');
        importFileInput.focus();
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    importButton.disabled = true;
    setImportResult('엑셀을 업로드하는 중입니다.');

    try{
        const response = await fetch('/admin/color-check/import', {
            method:'POST',
            headers:{
                'Accept':'application/json'
            },
            body:formData
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        const result = await response.json();
        const skippedDetails = (result.details || [])
            .filter(detail => detail.status === '제외');
        setImportResult(
            '업로드 완료: 신규 '
                + result.insertedCount.toLocaleString('ko-KR')
                + '건, 수정 '
                + result.updatedCount.toLocaleString('ko-KR')
                + '건, 변경 없음 '
                + result.unchangedCount.toLocaleString('ko-KR')
                + '건, 제외 '
                + result.skippedCount.toLocaleString('ko-KR')
                + '건',
            skippedDetails);
        importFileInput.value = '';
        await loadColorCheckItems();
    }catch(error){
        setImportResult(error.message || '엑셀 업로드에 실패했습니다.');
    }finally{
        importButton.disabled = false;
    }
}

function renderUsers(){
    destroyDataTable(userDataTable);
    userDataTable = null;
    userTableBody.innerHTML = '';

    userState.filteredItems.forEach((item, index) => {
        const row = document.createElement('tr');
        const values = [
            {text: item.userId, field: ''},
            {text: item.passwordStatus, field: 'password'},
            {text: item.userName, field: 'userName'},
            {text: item.userEmail, field: 'userEmail'},
            {text: formatRole(item.userRole), field: 'userRole'},
            {text: item.slackUserId, field: 'slackUserId'},
            {text: '', field: 'actions'}
        ];

        values.forEach(value => {
            const cell = document.createElement('td');
            if(value.field === 'actions'){
                const actions = document.createElement('div');
                actions.className = 'admin-row-actions';
                const deleteButton = document.createElement('button');
                deleteButton.className = 'admin-icon-btn danger';
                deleteButton.type = 'button';
                deleteButton.title = '삭제';
                deleteButton.innerHTML = '<i class="fas fa-trash"></i>';
                deleteButton.addEventListener('click', () => deleteUser(item));
                actions.appendChild(deleteButton);
                cell.appendChild(actions);
            }else{
                cell.textContent = safeText(value.text);
            }
            if(value.field && value.field !== 'actions'){
                cell.classList.add('admin-editable-cell');
                cell.title = '더블클릭해서 수정';
                cell.addEventListener('dblclick', () => {
                    startUserCellEdit(cell, item, value.field);
                });
            }
            row.appendChild(cell);
        });

        userTableBody.appendChild(row);
    });
    initUserDataTable();
}

async function createUser(event){
    event.preventDefault();
    const userId = newUserId.value.trim();
    const password = newUserPassword.value.trim();
    const userName = newUserName.value.trim();
    const userEmail = newUserEmail.value.trim();
    const userRole = newUserRole.value;
    const slackUserId = newSlackUserId.value.trim();

    try{
        const response = await fetch('/admin/users', {
            method:'POST',
            headers:{
                'Content-Type':'application/json',
                'Accept':'application/json'
            },
            body:JSON.stringify({
                userId:userId,
                password:password,
                userName:userName,
                userEmail:userEmail,
                userRole:userRole,
                slackUserId:slackUserId
            })
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        userCreateForm.reset();
        newUserRole.value = 'Y';
        await loadUsers();
    }catch(error){
        userSummary.textContent = error.message || '사용자를 등록하지 못했습니다.';
    }
}

async function deleteUser(item){
    const userId = item.userId || '';

    if(!userId || !confirm(userId + ' 사용자를 삭제할까요?')){
        return;
    }

    try{
        const response = await fetch(
            '/admin/users/' + encodeURIComponent(userId),
            {method:'DELETE'});

        if(!response.ok){
            throw new Error(await response.text());
        }

        await loadUsers();
    }catch(error){
        userSummary.textContent = error.message || '사용자를 삭제하지 못했습니다.';
    }
}

function startUserCellEdit(cell, item, field){
    if(userState.editingCell){
        return;
    }

    const originalText = cell.textContent;
    userState.editingCell = cell;
    cell.innerHTML = '';

    const editor = field === 'userRole'
        ? document.createElement('select')
        : document.createElement('input');
    editor.className = 'admin-inline-editor';

    if(field === 'userRole'){
        ['Y', 'ADMIN'].forEach(role => {
            const option = document.createElement('option');
            option.value = role;
            option.textContent = formatRole(role);
            const currentRole = String(item.userRole || '').toUpperCase();
            option.selected = role === 'ADMIN'
                ? currentRole.includes('ADMIN')
                : !currentRole.includes('ADMIN');
            editor.appendChild(option);
        });
    }else{
        editor.type = field === 'password'
            ? 'password'
            : field === 'userEmail'
                ? 'email'
                : 'text';
        editor.value = field === 'password' ? '' : (item[field] || '');
        editor.placeholder = field === 'password'
            ? '새 비밀번호'
            : '';
    }

    let closed = false;
    const finish = async shouldSave => {
        if(closed){
            return;
        }
        closed = true;
        const value = editor.value.trim();

        if(!shouldSave || (field === 'password' && !value)){
            cell.textContent = originalText;
            userState.editingCell = null;
            return;
        }

        try{
            const updatedUser = await saveUserField(item.userId, field, value);
            Object.assign(item, updatedUser);
            cell.textContent = field === 'password'
                ? safeText(updatedUser.passwordStatus)
                : field === 'userRole'
                    ? formatRole(updatedUser.userRole)
                    : field === 'userEmail'
                        ? safeText(updatedUser.userEmail)
                        : field === 'slackUserId'
                            ? safeText(updatedUser.slackUserId)
                            : safeText(updatedUser.userName);
            if(userDataTable){
                userDataTable.cell(cell).data(cell.textContent).draw(false);
            }
            updateUserSummary(userDataTable
                ? userDataTable.page.info().recordsDisplay
                : userState.filteredItems.length);
        }catch(error){
            cell.textContent = originalText;
            userSummary.textContent = error.message || '사용자 정보를 저장하지 못했습니다.';
        }finally{
            userState.editingCell = null;
        }
    };

    editor.addEventListener('keydown', event => {
        if(event.key === 'Enter'){
            event.preventDefault();
            editor.blur();
        }
        if(event.key === 'Escape'){
            event.preventDefault();
            finish(false);
        }
    });
    editor.addEventListener('blur', () => finish(true));
    cell.appendChild(editor);
    editor.focus();
    if(editor.select){
        editor.select();
    }
}

async function saveUserField(userId, field, value){
    const response = await fetch('/admin/users/' + encodeURIComponent(userId), {
        method:'PATCH',
        headers:{
            'Content-Type':'application/json',
            'Accept':'application/json'
        },
        body:JSON.stringify({
            field:field,
            value:value
        })
    });

    if(!response.ok){
        throw new Error(await response.text());
    }

    return response.json();
}

function applyUserFilter(){
    userState.filteredItems = [...userState.items];
    updateUserSummary(userState.filteredItems.length);
    renderUsers();
}

function updateUserSummary(showing){
    const total = userState.items.length;
    const adminCount = userState.items
        .filter(item => String(item.userRole || '').toUpperCase().includes('ADMIN'))
        .length;
    const normalCount = total - adminCount;

    userSummary.textContent = '총 '
        + total.toLocaleString('ko-KR')
        + '명 중 '
        + showing.toLocaleString('ko-KR')
        + '명 표시';
    userTotalCount.textContent = total.toLocaleString('ko-KR');
    userAdminCount.textContent = adminCount.toLocaleString('ko-KR');
    userNormalCount.textContent = normalCount.toLocaleString('ko-KR');
    userFilteredCount.textContent = showing.toLocaleString('ko-KR');
}

async function loadUsers(){
    userRefresh.disabled = true;
    userSummary.textContent = '사용자 정보를 불러오는 중입니다.';

    try{
        const response = await fetch('/admin/users', {
            headers:{
                'Accept':'application/json'
            }
        });

        if(!response.ok){
            throw new Error(await response.text());
        }

        userState.items = await response.json();
        applyUserFilter();
    }catch(error){
        userTableBody.innerHTML = '';
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 5;
        cell.className = 'admin-empty-cell';
        cell.textContent = '사용자 DB를 불러오지 못했습니다.';
        row.appendChild(cell);
        userTableBody.appendChild(row);
        userSummary.textContent = error.message || '조회 중 오류가 발생했습니다.';
    }finally{
        userRefresh.disabled = false;
    }
}

document.getElementById('accountNewPassword')
    .addEventListener('input', updatePasswordMatchMessage);
document.getElementById('accountConfirmPassword')
    .addEventListener('input', updatePasswordMatchMessage);

accountBackdrop.addEventListener('click', function(event){
    if(event.target === accountBackdrop){
        closeAccountDialog();
    }
});

accountProfileFile.addEventListener('change', function(){
    const file = accountProfileFile.files[0];

    if(!file){
        return;
    }

    const errorElement =
        document.getElementById('accountFormError');
    const formData = new FormData();
    formData.append('file', file);
    errorElement.textContent = '';

    fetch('/api/user/profile-image', {
        method:'POST',
        body:formData
    })
    .then(response => readErrorMessage(
        response,
        '프로필 이미지 변경에 실패했습니다.'
    ))
    .then(() => {
        refreshProfileImages();
    })
    .catch(error => {
        errorElement.textContent = error.message;
    })
    .finally(() => {
        accountProfileFile.value = '';
    });
});

document.getElementById(
    'accountImageDeleteButton'
).addEventListener('click', function(){
    const errorElement =
        document.getElementById('accountFormError');
    errorElement.textContent = '';

    fetch('/api/user/profile-image', {
        method:'DELETE'
    })
    .then(response => readErrorMessage(
        response,
        '프로필 이미지 삭제에 실패했습니다.'
    ))
    .then(() => {
        hideProfileImages();
    })
    .catch(error => {
        errorElement.textContent = error.message;
    });
});

document.addEventListener('keydown', function(event){
    if(event.key === 'Escape' && !accountBackdrop.hidden){
        closeAccountDialog();
    }
});

accountForm.addEventListener('submit', function(event){
    event.preventDefault();

    const saveButton =
        document.getElementById('accountSaveButton');
    const errorElement =
        document.getElementById('accountFormError');
    const userName =
        document.getElementById('accountUserName').value.trim();
    const currentPassword =
        document.getElementById('accountCurrentPassword').value;
    const newPassword =
        document.getElementById('accountNewPassword').value;
    const confirmPassword =
        document.getElementById('accountConfirmPassword').value;

    errorElement.textContent = '';

    if(!updatePasswordMatchMessage()){
        errorElement.textContent =
            '신규 비밀번호와 확인 비밀번호가 일치하지 않습니다.';
        return;
    }

    if(newPassword && !currentPassword){
        errorElement.textContent =
            '현재 비밀번호를 입력해주세요.';
        return;
    }

    saveButton.disabled = true;
    saveButton.textContent = '수정 중...';

    fetch('/api/user/account', {
        method:'POST',
        headers:{
            'Content-Type':'application/json'
        },
        body:JSON.stringify({
            userName:userName,
            currentPassword:currentPassword,
            newPassword:newPassword,
            confirmPassword:confirmPassword
        })
    })
    .then(async response => {
        const text = await response.text();

        if(!response.ok){
            try{
                const body = JSON.parse(text);
                throw new Error(
                    body.message
                    || body.error
                    || text
                    || '계정 정보를 수정하지 못했습니다.'
                );
            }catch(error){
                if(error instanceof SyntaxError){
                    throw new Error(
                        text || '계정 정보를 수정하지 못했습니다.'
                    );
                }
                throw error;
            }
        }

        return text ? JSON.parse(text) : {};
    })
    .then(user => {
        document.getElementById(
            'currentUserName'
        ).textContent = user.userName;
        document.getElementById(
            'accountUserName'
        ).value = user.userName;
        closeAccountDialog();
    })
    .catch(error => {
        errorElement.textContent = error.message;
    })
    .finally(() => {
        saveButton.disabled = false;
        saveButton.textContent = '수정';
    });
});

refreshButton.addEventListener('click', loadColorCheckItems);
editForm.addEventListener('submit', saveItem);
importForm.addEventListener('submit', importColorCheckExcel);
importButton.addEventListener('click', () => importFileInput.click());
importFileInput.addEventListener('change', importColorCheckExcel);
cancelButton.addEventListener('click', resetForm);
berAsisTobeRefresh.addEventListener('click', loadBerAsisTobeItems);
berAsisTobeImportForm.addEventListener('submit', importBerAsisTobeExcel);
berAsisTobeImportButton.addEventListener(
    'click',
    () => berAsisTobeImportFile.click());
berAsisTobeImportFile.addEventListener('change', importBerAsisTobeExcel);
berSentenceImportToggle.addEventListener(
    'click',
    openBerSentenceImportPopup);
qsgDbRefresh.addEventListener('click', loadQsgDbItems);
qsgDbImportForm.addEventListener('submit', importQsgDbExcel);
qsgDbImportButton.addEventListener('click', () => qsgDbImportFile.click());
qsgDbImportFile.addEventListener('change', importQsgDbExcel);
replaceDarkSymbolRefresh.addEventListener('click', loadReplaceDarkSymbolItems);
window.addEventListener('message', function(event){
    if(event.origin !== window.location.origin){
        return;
    }
    if(event.data && event.data.type === 'BER_SENTENCE_IMPORT_DONE'){
        loadBerAsisTobeItems();
    }
});
userRefresh.addEventListener('click', loadUsers);
userCreateForm.addEventListener('submit', createUser);
document.querySelectorAll('[data-admin-view]').forEach(button => {
    button.addEventListener('click', () => switchAdminView(button.dataset.adminView));
});
loadUsers();
