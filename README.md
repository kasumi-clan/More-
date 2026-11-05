# Moon Zoom — By GODLY (bản v2)

> CHƯA được biên dịch hay chạy thử trên máy thật (môi trường tạo code không có Android SDK/Gradle/mạng).
> Build bằng GitHub Actions và gửi log lỗi nếu có để sửa.

## Giao diện
- Chỉ một chế độ: ZOOM. Khung hình: 3:4, 1:1, 9:16, FULL (preview và ảnh chụp cùng crop giữa).
- Zoom: mặc định chỉ có 1x và 5x; kéo ngang thì bung thước cung tròn (kim vàng) tới đúng 100x, tự thu lại sau ~2 giây.
- Hàng nút dưới: thư viện | AI (bật/tắt) | nút chụp | WM (watermark) | đổi cam. Flash ở góc trên phải.
- Liquid Glass bằng gradient + viền phản chiếu (không blur thời gian thực để không giật).

## Zoom mượt
- SmoothZoomController nội suy theo thang log, đồng bộ khung hình (withFrameNanos), không gây recomposition.
- Camera nhận zoom tối đa ~35 lần/giây; phần vượt mức tối đa của camera là phóng số trên preview (GPU) và crop thật khi chụp.

## AI (MoonAIEnhancer) — thuật toán thật, KHÔNG phải mạng nơ-ron
Chụp 5 khung -> chấm độ nét, bỏ khung nhòe -> căn chỉnh dưới pixel -> gộp khung vào lưới phân giải cao
-> ước lượng nhiễu (MAD) và độ nhòe (độ rộng cạnh) -> bilateral giảm nhiễu giữ cạnh + làm mịn sắc độ
-> nhận diện mặt (android.media.FaceDetector) để xử lý mặt nhẹ tay -> khử nhòe Richardson–Lucy
-> cân tông theo phơi sáng -> làm nét có ngưỡng theo nhiễu -> phóng bicubic nếu ảnh vẫn nhỏ.
% tiến độ lấy theo từng bước thật. Giới hạn: xử lý tối đa cạnh dài ~2600px; chưa có segmentation,
super-resolution bằng model, RAW, dark-frame. Ở zoom rất cao (50x–100x) ảnh vẫn rất mờ: không thêm chi tiết thật.

## Watermark
Chuyển từ Godly-Watermark.html: cùng công thức bố cục, nội dung (tên máy; "PHOTO • WxH • mm"; ngày giờ)
và shader kính khúc xạ (chạy CPU). Tên máy lấy từ EXIF của ảnh vừa chụp (có bảng tên Samsung). Chưa có logo.

## Giới hạn
Chỉ Android 10+ (minSdk 29). Khoá dọc. Ảnh lưu ở Pictures/MoonZoom. Đã bỏ NIGHT, MILKY WAY, PRO.
