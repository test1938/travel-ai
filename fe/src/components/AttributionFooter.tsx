/** Data credit line required by the OSM (ODbL) and Wikivoyage/Wikipedia (CC BY-SA) licences. */
export function AttributionFooter() {
  return (
    <footer className="mt-auto px-[var(--margin)] py-6 text-[13px] leading-relaxed text-faint">
      <p className="mx-auto max-w-[1152px]">
        Dữ liệu bản đồ ©{' '}
        <a
          className="underline"
          href="https://www.openstreetmap.org/copyright"
          target="_blank"
          rel="noreferrer"
        >
          OpenStreetMap contributors
        </a>{' '}
        (ODbL). Nội dung hướng dẫn từ Wikivoyage và Wikipedia (CC BY-SA 4.0). Giá vé có ngày cập
        nhật, có thể thay đổi.
      </p>
    </footer>
  )
}
