/** Data credit line required by the OSM (ODbL) and Wikivoyage/Wikipedia (CC BY-SA) licences. */
export function AttributionFooter() {
  return (
    <footer className="mt-auto border-t border-slate-200 px-4 py-3 text-center text-xs text-slate-500">
      Dữ liệu bản đồ ©{' '}
      <a
        className="underline"
        href="https://www.openstreetmap.org/copyright"
        target="_blank"
        rel="noreferrer"
      >
        OpenStreetMap contributors
      </a>{' '}
      (ODbL). Nội dung hướng dẫn từ Wikivoyage và Wikipedia (CC BY-SA 4.0). Giá vé có ngày cập nhật,
      có thể thay đổi.
    </footer>
  )
}
