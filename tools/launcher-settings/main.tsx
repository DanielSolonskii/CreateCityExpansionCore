import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/manrope/cyrillic-400.css";
import "@fontsource/manrope/cyrillic-500.css";
import "@fontsource/manrope/cyrillic-600.css";
import "@fontsource/manrope/cyrillic-700.css";
import "@fontsource/manrope/cyrillic-800.css";
import "@fontsource/oswald/cyrillic-500.css";
import "@fontsource/oswald/cyrillic-600.css";
import "@fontsource/oswald/cyrillic-700.css";
import App from "./App";
import "./styles.css";
import "./auth-readability.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode><App /></StrictMode>,
);
