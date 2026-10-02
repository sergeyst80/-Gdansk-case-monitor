// Call this function with the adapter on an isolated about:blank browser page.
async function(inspect) {
    const results = [];
    const assert = (condition, name) => { if (!condition) throw new Error(name); results.push(name); };
    const read = () => JSON.parse(inspect('read', '', ''));
    document.body.innerHTML = '<div>Loading…</div>';
    assert(read().state === 'LOADING', 'Loading page is not a successful snapshot');
    document.body.innerHTML = '<input type="text"><input type="password"><button>Log in</button>';
    assert(read().state === 'LOGIN', 'Recognizes English login form');
    let clicked = 0;
    document.querySelector('button').click = () => clicked++;
    assert(JSON.parse(inspect('login', 'fixture-user', 'fixture-password')).state === 'SUBMITTED', 'Fills HTML form');
    await new Promise(resolve => setTimeout(resolve, 400));
    assert(clicked === 1 && document.querySelector('input').value === 'fixture-user', 'Submits once after field synchronization');
    assert(!read().snapshot, 'Login form never exports credentials');
    document.body.innerHTML = '<vaadin-text-field></vaadin-text-field><vaadin-password-field></vaadin-password-field><vaadin-button>Log in</vaadin-button>';
    for (const field of document.querySelectorAll('vaadin-text-field,vaadin-password-field')) {
        field.style.display = 'inline-block';
        field.attachShadow({mode:'open'}).innerHTML = '<input type="' + (field.tagName === 'VAADIN-PASSWORD-FIELD' ? 'password' : 'text') + '">';
        field.value = '';
    }
    const button = document.querySelector('vaadin-button'); button.style.display = 'inline-block';
    clicked = 0; button.click = () => clicked++;
    assert(JSON.parse(inspect('login', 'fixture-user', 'fixture-password')).state === 'SUBMITTED', 'Fills Vaadin shadow inputs');
    await new Promise(resolve => setTimeout(resolve, 400));
    assert(clicked === 1 && document.querySelector('vaadin-text-field').value === 'fixture-user', 'Updates Vaadin host value');
    document.querySelector('vaadin-password-field').invalid = true;
    assert(read().state === 'REJECTED', 'Reports rejected login without leaking credentials');
    document.body.innerHTML = '<vaadin-text-field></vaadin-text-field>';
    const field = document.querySelector('vaadin-text-field'); field.style.display = 'inline-block';
    field.value = 'In progress'; field.label = 'Case status';
    field.attachShadow({mode:'open'}).innerHTML = '<input value="In progress">';
    const snapshot = read();
    assert(snapshot.state === 'READY' && snapshot.snapshot.fields.length === 1, 'Does not duplicate shadow field values');
    assert(snapshot.snapshot.fields[0].label === 'Case status', 'Reads Vaadin label property');
    document.body.innerHTML = '<div id="outer"></div>';
    document.querySelector('#outer').attachShadow({mode:'open'}).innerHTML = '<input aria-label="Case status" value="Pending">';
    assert(read().state === 'READY', 'Finds data inside nested shadow roots');
    document.body.innerHTML = '<div><div><label>Name and surname</label></div><div><input value="Sample name"></div></div>'
        + '<div><div><label>Stage of your case</label></div><div><input value="Pending"></div></div>'
        + '<div><div><textarea>Sample description</textarea></div></div>'
        + '<div><div><label>Notes</label></div><div><textarea></textarea></div></div>'
        + '<div><div><label>Documents</label></div><div><input value="Sample document"></div></div>';
    const labeled = read().snapshot.fields;
    assert(labeled.map(f => f.key).join(',') === 'name,stage,stageDescription,documents', 'External labels remain correct when notes are empty');
    assert(labeled[2].label === 'Stage description', 'Unlabeled stage explanation has a semantic label');
    document.body.innerHTML = '<label id="case-label">Case number</label><input aria-labelledby="case-label" value="Sample number">';
    assert(read().snapshot.fields[0].key === 'caseNumber', 'Reads aria-labelledby');
    document.body.innerHTML = '<div><div><label>Uwagi</label></div><div><textarea>Sample note</textarea></div></div>';
    assert(read().snapshot.fields[0].key === 'notes', 'Recognizes Polish labels');
    document.body.innerHTML = '<input autocomplete="one-time-code">';
    assert(read().state === 'CHALLENGE', 'Reports MFA instead of timeout');
    return {passed:results.length, tests:results};
}
