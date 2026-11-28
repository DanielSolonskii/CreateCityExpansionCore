// The Apple Silicon release has a versioned URL. The HTML link also works offline from the manifest.
(async () => {
  try {
    const response = await fetch('/downloads/macos.json');
    if (!response.ok) return;
    const { manifest } = await response.json();
    if (manifest?.approved !== true || !/^\d+\.\d+\.\d+$/.test(manifest.version)) return;
    const installer = 'https://api.recreated.ru/launcher/macos-aarch64/versions/' +
      manifest.version + '/RE-CREATED-Launcher-aarch64.app.zip';
    if (manifest.installerUrl === installer) document.getElementById('mac-download').href = installer;
  } catch {
    // Keep the known published installer when the release metadata is unavailable.
  }
})();
