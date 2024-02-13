function init() {
  document.querySelectorAll('.collapse-header').forEach(elem => {
    elem.onclick = () => {
      console.log('clicked');
      elem.classList.toggle('active');
      document.getElementById(elem.dataset.for).classList.toggle('hidden');
      document.dispatchEvent(new CustomEvent('header-toggle', {
        detail: document.getElementById(elem.dataset.for)
      }))
    }
  });

  document.querySelector('#database-selector').onchange = (e) => {
    window.location.href = window.location.href.split('?')[0] + '?db=' + e.target.value;
  }

  if (typeof Handsontable !== 'undefined') {
    Handsontable.renderers.registerRenderer('nullRenderer', nullRenderer)
  }

  document.querySelector('#theme-toggle').onclick = function() {
    if (document.body.getAttribute('data-theme') === 'dark') {
      document.body.removeAttribute('data-theme');
      localStorage.removeItem('theme');
    } else {
      document.body.setAttribute('data-theme', 'dark');
      localStorage.setItem('theme', 'dark');
    }
  }

  const savedTheme = localStorage.getItem('theme');
  if (savedTheme) {
    document.body.setAttribute('data-theme', savedTheme);
  }
}


function htmlToHandsonData(table) {
  const headers = []
  const rows = []

  for (const row of table.querySelectorAll('tr')) {
    for (const th of row.querySelectorAll('th')) {
      headers.push(th.innerText)
    }

    const cells = []

    for (const td of row.querySelectorAll('td')) {
      cells.push(td.innerText)
    }

    if (cells.length > 0) {
      rows.push(cells)
    }
  }

  return {
    headers: headers, 
    rows: rows
  }
}

function nullRenderer(hot, td, row, column, props, value, cellProperties) {
  if (value === 'null') {
    td.innerHTML = `<em class="null">null</em>`
  } else {
    td.innerHTML = value
  }
}

init();
