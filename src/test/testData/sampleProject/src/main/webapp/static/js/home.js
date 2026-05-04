// Sample frontend script. Mitos should:
//  - link the file to home.jsp via the <script src> tag (SCRIPTLET_CALL edge);
//  - link inline onsubmit/onclick handlers to submitGreeting / trackClick (JS_INVOCATION);
//  - link the fetch() call to HomeController#greet via /home/api/greet (AJAX_REQUEST, dashed).

function submitGreeting(event) {
    event.preventDefault();
    const name = event.target.name.value;
    fetch("/home/api/greet", {
        method: "POST",
        body: name
    })
        .then(function (r) { return r.text(); })
        .then(showResult);
}

function showResult(text) {
    const out = document.createElement('p');
    out.textContent = text;
    document.body.appendChild(out);
}

function trackClick() {
    console.log("greet button clicked");
}
