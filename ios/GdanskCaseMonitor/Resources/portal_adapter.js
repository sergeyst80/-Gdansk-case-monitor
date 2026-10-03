function(mode, login, password) {
    const clean = s => String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/[ \t]+/g, ' ').trim();
    const roots = [document];
    for (let n = 0; n < roots.length; n++) roots[n].querySelectorAll('*').forEach(e => { if (e.shadowRoot) roots.push(e.shadowRoot); });
    const all = selector => roots.flatMap(r => Array.from(r.querySelectorAll(selector)));
    const visible = e => {
        const s = getComputedStyle(e), r = e.getBoundingClientRect();
        return !e.hidden && s.display !== 'none' && s.visibility !== 'hidden' && r.width > 0 && r.height > 0;
    };
    const pass = all('vaadin-password-field').find(visible) || all('input[type=password]').find(visible);
    const labelFor = e => {
        const own = clean(e.label || e.getAttribute('label') || e.getAttribute('aria-label') || (e.labels && e.labels[0] && e.labels[0].textContent));
        if (own) return own;
        const ids = (e.getAttribute('aria-labelledby') || '').split(/\s+/).filter(Boolean);
        const linked = ids.map(id => { const root = e.getRootNode(); return (root.getElementById && root.getElementById(id)) || document.getElementById(id); }).filter(Boolean).map(n => clean(n.textContent)).filter(Boolean).join(' ');
        if (linked) return linked;
        // Portal layout: <div><div><label>...</label></div><div><vaadin-.../></div></div>.
        // Never borrow the label of a preceding field or section.
        for (let node = e, depth = 0; node && depth < 5; node = node.parentElement, depth++) {
            const previous = node.previousElementSibling;
            if (!previous) continue;
            const candidate = previous.matches('label,h1,h2,h3,h4,h5,h6') ? previous :
                (!previous.querySelector('input,textarea,vaadin-text-field,vaadin-text-area,vaadin-date-picker') && previous.querySelector('label,h1,h2,h3,h4,h5,h6'));
            if (candidate && visible(candidate) && clean(candidate.textContent)) return clean(candidate.textContent);
        }
        return clean(e.getAttribute('placeholder'));
    };
    const keyFor = label => {
        const s = label.toLowerCase().replace(/:$/, '').trim();
        if (/^(name and surname|imię i nazwisko|imie i nazwisko)$/.test(s)) return 'name';
        if (/^(case number|numer sprawy)$/.test(s)) return 'caseNumber';
        if (/^(date of filing the application|data złożenia wniosku)$/.test(s)) return 'filedDate';
        if (/^(stage of your case|etap sprawy|etap twojej sprawy)$/.test(s)) return 'stage';
        if (/^(stage description|opis etapu)$/.test(s)) return 'stageDescription';
        if (/^(notes|uwagi)$/.test(s)) return 'notes';
        if (/^(documents|dokumenty)$/.test(s)) return 'documents';
        return '';
    };
    const fields = [], seen = new Set();
    all('vaadin-text-field,vaadin-text-area,vaadin-date-picker,vaadin-combo-box,vaadin-select,input,textarea').forEach(e => {
        if (!visible(e) || e === pass || e.type === 'password') return;
        const root = e.getRootNode();
        if (root.host && /^VAADIN-/.test(root.host.tagName)) return;
        if ((e.tagName === 'INPUT' || e.tagName === 'TEXTAREA') && e.closest('vaadin-text-field,vaadin-text-area,vaadin-date-picker,vaadin-combo-box,vaadin-select')) return;
        const input = e.shadowRoot && e.shadowRoot.querySelector('input,textarea');
        const value = clean(e.value || (input && input.value));
        let label = labelFor(e), semanticKey = keyFor(label);
        if (!label && /TEXTAREA|TEXT-AREA/.test(e.tagName) && fields.length && fields[fields.length - 1].key === 'stage') {
            label = 'Stage description'; semanticKey = 'stageDescription';
        }
        const key = label + '|' + value;
        if (value && !seen.has(key)) { seen.add(key); fields.push({key:semanticKey, label, value}); }
    });
    const lines = clean(document.body ? document.body.innerText : '').split('\n').map(clean).filter(s => s.length > 1).slice(0, 120);
    const challenge = all('iframe').some(e => visible(e) && /recaptcha|hcaptcha|captcha/i.test(e.src)) || all('input[autocomplete=one-time-code]').some(visible);
    if (challenge) return JSON.stringify({state:'CHALLENGE'});
    if (!pass) return JSON.stringify({state:fields.length ? 'READY' : 'LOADING', snapshot:{loggedIn:true, title:document.title, url:location.href, fields, lines}});
    if (mode !== 'login') {
        const errors = all('[role=alert],[part=error-message],vaadin-notification-card').filter(visible).map(e => clean(e.innerText || e.textContent)).filter(Boolean);
        const invalid = all('vaadin-text-field,vaadin-password-field').some(e => visible(e) && e.invalid);
        return JSON.stringify({state:errors.length || invalid ? 'REJECTED' : 'LOGIN'});
    }
    const user = all('vaadin-text-field').find(e => visible(e) && !e.disabled && !e.readonly) || all('input[type=text],input[type=email],input:not([type])').find(e => visible(e) && !e.disabled && !e.readOnly);
    const buttons = all('vaadin-button,button,input[type=submit]').filter(e => visible(e) && !e.disabled);
    const button = buttons.find(e => /zaloguj|logowanie|log\s*in|sign\s*in|увійти/i.test(clean(e.innerText || e.textContent || e.value || e.getAttribute('aria-label')))) || buttons.find(e => e.type === 'submit' || (e.matches('vaadin-button[theme~=primary]') && e.closest('vaadin-form-layout')));
    if (!user || !button) return JSON.stringify({state:'WAIT_FORM'});
    const set = (e, v) => {
        if (/^VAADIN-/.test(e.tagName)) e.value = v;
        const input = e.shadowRoot ? e.shadowRoot.querySelector('input') : e;
        if (!input) return false;
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, v);
        input.dispatchEvent(new Event('input', {bubbles:true, composed:true}));
        input.dispatchEvent(new Event('change', {bubbles:true, composed:true}));
        input.dispatchEvent(new Event('blur', {bubbles:true, composed:true}));
        return input.value === v;
    };
    if (!set(user, login) || !set(pass, password)) return JSON.stringify({state:'WAIT_FORM'});
    // Let Vaadin synchronize values before sending the click RPC.
    setTimeout(() => button.click(), 300);
    return JSON.stringify({state:'SUBMITTED'});
}
