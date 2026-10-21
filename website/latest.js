// The latest release from GitHub: its version, size and date under the Download button.
// If GitHub can't be reached, the button still works (it always points to the latest APK).
(async () => {
  const line = document.getElementById("release");
  try {
    const response = await fetch("https://api.github.com/repos/jordanmontt/Blackwater-Macros/releases/latest", {
      headers: { Accept: "application/vnd.github+json" },
    });
    if (!response.ok) return;
    const release = await response.json();
    const apk = release.assets.find((asset) => asset.name === "blackwater-macros.apk");
    if (!apk) return;
    const date = new Date(release.published_at).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
    const megabytes = Math.round(apk.size / 1e6);
    line.textContent = `Version ${release.tag_name.replace(/^v/, "")}, ${megabytes} MB, released ${date}. For Android 7.0 and later, on 64-bit phones.`;
  } catch {
    // Offline or rate-limited: keep the static text.
  }
})();
