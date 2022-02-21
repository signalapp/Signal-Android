function init() {
  document.querySelectorAll('.collapse-header').forEach(elem => {
    elem.onclick = () => {
      console.log('clicked');
      elem.classList.toggle('active');
      document.getElementById(elem.dataset.for).classList.toggle('hidden');
    }
  });

  document.querySelector('#database-selector').onchange = (e) => {
    window.location.href = window.location.href.split('?')[0] + '?db=' + e.target.value;
  }
}

init();
