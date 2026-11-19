const copyLink = document.getElementById('copy-link');
const manualLink = document.getElementById('manual-link');
const copyResult = document.getElementById('copy-result');
copyLink.hidden = false;
copyLink.addEventListener('click', async () => {
  const address = document.querySelector('link[rel="canonical"]').href;
  try {
    await navigator.clipboard.writeText(address);
    manualLink.hidden = true;
    copyResult.textContent = 'Ссылка скопирована.';
  } catch {
    copyResult.textContent = 'Скопируй адрес вручную.';
    manualLink.value = address;
    manualLink.hidden = false;
    manualLink.focus();
    manualLink.select();
  }
});
