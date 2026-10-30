import { useEffect, type RefObject } from 'react'

/**
 * Entrances for `[data-reveal]` elements inside `root`: each rises once as it enters the screen. The page is
 * only marked `has-motion` from script, so content stays visible when script or IntersectionObserver is missing.
 */
export function useReveal(root: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const el = root.current
    if (!el || typeof IntersectionObserver === 'undefined') {
      return
    }
    document.documentElement.classList.add('has-motion')
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-in')
            observer.unobserve(entry.target)
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px' },
    )
    el.querySelectorAll('[data-reveal]').forEach((node) => observer.observe(node))
    return () => observer.disconnect()
  }, [root])
}
