import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/manrope/cyrillic-400.css";
import "@fontsource/manrope/cyrillic-500.css";
import "@fontsource/manrope/cyrillic-600.css";
import "@fontsource/manrope/cyrillic-700.css";
import "@fontsource/manrope/cyrillic-800.css";
import "@fontsource/manrope/latin-400.css";
import "@fontsource/manrope/latin-500.css";
import "@fontsource/manrope/latin-600.css";
import "@fontsource/manrope/latin-700.css";
import "@fontsource/manrope/latin-800.css";
import App from "./App";
import "./styles.css";
import "./auth-readability.css";
import "./launcher-polish.css";
import "./launcher-refresh.css";
import "./companion.css";
import "./quiet-motion.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode><App /></StrictMode>,
);
