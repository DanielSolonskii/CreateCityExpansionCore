import { useLayoutEffect, useRef } from "react";

const stack: HTMLElement[] = [];
const selector = 'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), summary, [tabindex]:not([tabindex="-1"])';

/** Shared keyboard contract for animated overlays that stay mounted when closed. */
export function useOverlay<T extends HTMLElement>(open: boolean, onClose: () => void) {
  const ref = useRef<T>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useLayoutEffect(() => {
    const root = ref.current;
    if (!root) return;
    root.inert = !open;
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    stack.push(root);
    const candidates = () => [...root.querySelectorAll<HTMLElement>(selector)].filter(el => {
      const style = getComputedStyle(el);
      return !el.closest('[inert]') && (!el.closest('details:not([open])') || el.tagName === "SUMMARY") && style.display !== "none" && style.visibility !== "hidden";
    });
    const focus = () => (root.querySelector<HTMLElement>('[autofocus],input:not(:disabled)') ?? candidates()[0] ?? root).focus();
    root.tabIndex = -1;
    focus();
    const top = () => stack.at(-1) === root && !document.querySelector('dialog[open]');
    const key = (event: KeyboardEvent) => {
      if (!top()) return;
      if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close.current(); }
      if (event.key === "Tab") {
        const items = candidates(), first = items[0], last = items.at(-1);
        if (!first) { event.preventDefault(); root.focus(); }
        else if (event.shiftKey && (document.activeElement === first || document.activeElement === root)) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    };
    const contain = (event: FocusEvent) => { if (top() && !root.contains(event.target as Node)) focus(); };
    document.addEventListener("keydown", key, true);
    document.addEventListener("focusin", contain);
    return () => {
      stack.splice(stack.indexOf(root), 1);
      document.removeEventListener("keydown", key, true);
      document.removeEventListener("focusin", contain);
      root.inert = true;
      // Restore after React has committed the closed state; doing it in cleanup
      // can be overwritten by React's own selection/focus restoration.
      queueMicrotask(() => {
        if (stack.length === 0 && previous?.isConnected && !previous.closest('[inert]')) previous.focus();
      });
    };
  }, [open]);
  return ref;
}
