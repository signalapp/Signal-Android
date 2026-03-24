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

init();
