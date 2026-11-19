export const coverColours = ["#b58b60", "#719b87", "#708eb5", "#a783af"];
export function readLook(account: string) {
  const empty = { colour: coverColours[0], bio: "", interests: "", minimal: false };
  if (!account) return empty;
  try {
    const look = JSON.parse(localStorage.getItem("recreated.profile-look.v1:" + account) ?? "{}");
    return {
      colour: coverColours.includes(look.colour) ? String(look.colour) : empty.colour,
      bio: typeof look.bio === "string" ? look.bio.slice(0, 120) : "",
      interests: typeof look.interests === "string" ? look.interests.slice(0, 80) : "",
      minimal: look.minimal === true,
    };
  } catch { return empty; }
}
