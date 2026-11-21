# Guild Master — Giai đoạn 1 (MVP)

Game idle quản lý hội mạo hiểm trên Android, chơi offline, 1 người. Nội dung (tên, nhân vật, quái, vùng) là của riêng dự án.

## Phạm vi

Giai đoạn 1: tuyển mạo hiểm giả, xếp đội, gửi đi nhiệm vụ có thời gian, mô phỏng trận theo lượt khi đội về, lên cấp, trang bị 6 ô, thông báo khi đội về.

Ngoài phạm vi (giai đoạn sau): lò rèn (nâng cấp/phân rã), kết hợp nghề, chuỗi nhiệm vụ/boss, công trình hội quán.

## Hạ tầng

- Repo riêng `GuildMaster/`, project Gradle (Kotlin DSL) 2 module:
  - `core/` — Kotlin/JVM thuần, không phụ thuộc Android: model, engine trận, luật sinh nhiệm vụ/ứng viên/đồ, tiến trình thời gian.
  - `app/` — Android (applicationId `vn.guildmaster.game`, minSdk 26, targetSdk 34): UI dựng bằng code, lưu file, thông báo.
- Không thư viện ngoài. `core` không đụng JSON; (de)serialize nằm ở `app` bằng `org.json` có sẵn của Android.
- Quyền: `POST_NOTIFICATIONS` (báo đội về dùng `setAndAllowWhileIdle`, không cần quyền báo thức chính xác); `INTERNET` + `REQUEST_INSTALL_PACKAGES` chỉ dùng cho tự cập nhật.
- Tự cập nhật: CI dùng `run_number` làm `versionCode`, tạo GitHub Release tag `v<versionCode>` kèm APK đã ký. App mở lên gọi `releases/latest`, có bản mới hơn thì hiện nút ở tab Hội → tải APK vào `PackageInstaller` → hệ thống hỏi xác nhận.
- Bản build đặt `isDebuggable = false`. GitHub Actions build APK, ký bằng khoá cố định `app/signing.p12` commit trong repo (đơn giản, không cần Secrets; chấp nhận rủi ro người khác ký được bản giả).
- Không viết test trừ khi được yêu cầu; `core` tách riêng để có thể test sau.

## 1. Nhân vật (Hero)

| Trường | Ghi chú |
|---|---|
| `id`, `name` | tên ghép ngẫu nhiên từ danh sách âm tiết |
| `cls` | WARRIOR, MAGE, ARCHER, PRIEST |
| `rarity` | COMMON, RARE, EPIC, LEGENDARY |
| `level` 1–50, `exp` | EXP lên cấp n→n+1 = `50 × n^1.6` (làm tròn) |
| `traits` | 0 / 1 / 1 / 2 đặc tính theo độ hiếm |
| `equipment` | map ô → itemId |
| `injuredUntil` | epoch ms, 0 = khoẻ |

**Chỉ số gốc cấp 1** (trước hệ số độ hiếm):

| Nghề | HP | ATK | DEF | SPD | Crit% | CritDMG% |
|---|---|---|---|---|---|---|
| Chiến binh | 220 | 18 | 16 | 95 | 5 | 150 |
| Pháp sư | 120 | 28 | 6 | 100 | 8 | 160 |
| Cung thủ | 140 | 24 | 8 | 115 | 15 | 170 |
| Tu sĩ | 140 | 14 | 10 | 105 | 5 | 150 |

- Tăng mỗi cấp: HP/ATK/DEF +8% chỉ số gốc; SPD/Crit không tăng theo cấp.
- Hệ số độ hiếm (HP/ATK/DEF): 1.0 / 1.15 / 1.35 / 1.6.
- Chỉ số cuối = (gốc × hệ số độ hiếm × hệ số cấp + phẳng từ đồ) × (1 + % từ đồ + % đặc tính).

**Kỹ năng** (đòn thường luôn có, hồi 0; số = hệ số ATK, hồi tính theo lượt của nhân vật):

| Nghề | Kỹ năng 1 | Kỹ năng 2 | Kỹ năng 3 |
|---|---|---|---|
| Chiến binh | Khiêu khích: quái buộc đánh mình 2 lượt, +30% DEF 2 lượt, hồi 4 | Đập khiên: 1.4× 1 mục tiêu, 30% choáng 1 lượt, hồi 3 | Tường thép: cả đội giảm 25% sát thương nhận 2 lượt, hồi 6 |
| Pháp sư | Cầu lửa: 0.9× mọi kẻ địch, hồi 3 | Băng giá: 1.2× 1 mục tiêu, −30% SPD 2 lượt, hồi 3 | Thiên thạch: 1.8× mọi kẻ địch, hồi 7 |
| Cung thủ | Bắn đôi: 2 phát 0.8×, hồi 2 | Ngắm bắn: 2.2×, +30% crit, hồi 4 | Mưa tên: 0.7× mọi kẻ địch, hồi 4 |
| Tu sĩ | Hồi máu: hồi 1.8× ATK + 10% HP tối đa cho đồng đội thấp HP nhất, hồi 2 | Ban phúc: cả đội +20% ATK 3 lượt, hồi 5 | Hồi sinh: hồi sinh 1 đồng đội với 40% HP, 1 lần/trận |

Kỹ năng mở theo cấp: kỹ năng 1 từ cấp 1, kỹ năng 2 từ cấp 5, kỹ năng 3 từ cấp 15.

**Đặc tính** (bị động, chọn ngẫu nhiên không trùng): Gan dạ +10% HP · Hung hãn +10% ATK · Cứng cỏi +10% DEF · Nhanh nhẹn +8 SPD · Tinh mắt +6% Crit · Tàn nhẫn +25% CritDMG · Học nhanh +15% EXP · May mắn +10% tỉ lệ rơi đồ (cộng dồn trong đội).

## 2. Trang bị (Item)

- Ô: WEAPON, HELMET, ARMOR, GLOVES, BOOTS, ACCESSORY. Vũ khí gắn với nghề (kiếm / trượng / cung / chuỳ), chỉ nghề đó mặc được; ô khác ai cũng mặc.
- `rarity` (4 bậc), `itemLevel` = cấp nhiệm vụ rơi ra.
- Chỉ số chính theo ô: vũ khí ATK, mũ HP, giáp DEF, găng Crit%, giày SPD, phụ kiện CritDMG%. Giá trị = cơ sở ô × (1 + 0.1 × itemLevel) × hệ số độ hiếm (1.0/1.3/1.7/2.2).
- Chỉ số phụ: 0 / 1 / 2–3 / 4 theo độ hiếm, chọn ngẫu nhiên không trùng với chỉ số chính, từ: HP%, ATK%, DEF%, SPD, Crit%, CritDMG%, HP phẳng, ATK phẳng.
- Kho đồ tối đa 60 món; đầy thì đồ rơi chuyển thành vàng (bán tự động).
- Bán đồ: vàng = 10 × itemLevel × (1/2/4/8 theo độ hiếm).

## 3. Engine trận (`core/battle`)

- Đầu vào: danh sách hero (đã tính chỉ số cuối), danh sách quái, `seed: Long`. Đầu ra: `BattleResult(win, rounds, survivors, log: List<BattleEvent>)`. Cùng đầu vào → cùng kết quả (RNG riêng `SplittableRandom(seed)`, không dùng thời gian thực).
- Lượt: thanh hành động; mỗi tick mọi đơn vị còn sống cộng SPD vào thanh, ai ≥ 1000 thì hành động (trừ 1000), hoà thì hero trước, rồi theo thứ tự vị trí. Tối đa 200 lượt hành động (cộng cả 2 phe) chưa phân thắng bại → thua.
- Sát thương = ATK × hệ số × 100 / (100 + DEF mục tiêu) × (CritDMG nếu chí mạng) × biến thiên ngẫu nhiên 0.95–1.05, tối thiểu 1.
- Chọn mục tiêu: quái ưu tiên kẻ khiêu khích, không thì ngẫu nhiên có trọng số (hàng đầu = Chiến binh trọng số 3, khác 1). Hero đánh đơn chọn quái HP thấp nhất.
- AI hero theo thứ tự ưu tiên: Tu sĩ — Hồi sinh nếu có người ngã → Hồi máu nếu đồng đội < 50% HP → Ban phúc; Chiến binh — Khiêu khích nếu chưa ai bị khiêu khích; còn lại — kỹ năng hệ số cao nhất đã hồi xong, không thì đòn thường. Kỹ năng đánh lan chỉ dùng khi ≥ 2 kẻ địch.
- Quái: chỉ số theo bảng loài × cấp; một số loài có 1 kỹ năng (vd Sói: cắn 1.3× hồi 3; Pháp sư goblin: cầu lửa 0.7× lan hồi 4).
- Log: mỗi sự kiện (ai, kỹ năng, mục tiêu, sát thương/hồi, chí mạng, hiệu ứng, ngã) — đủ để UI dựng lại nhật ký dạng chữ.

## 4. Nhiệm vụ và thời gian

- **Vùng** (mở theo cấp hội): Rừng Sương (cấp 1–10), Hang Đá Đỏ (8–20), Đầm Lầy Tro (18–32), Thành Cổ Băng (30–50). Mỗi vùng 4–6 loài quái.
- **Bảng nhiệm vụ**: 5 nhiệm vụ; làm mới toàn bộ mỗi 4 giờ (theo mốc `boardSeed` + kỳ 4 giờ), nhiệm vụ đang làm không bị mất. Mỗi nhiệm vụ: vùng, cấp đề xuất, thời gian chọn từ {5p, 15p, 30p, 1g, 2g, 4g, 8g}, đội quái 1–5 con, thưởng.
- **Thưởng** (thắng): vàng = 20 × cấp × phút^0.7; EXP mỗi hero = 15 × cấp × phút^0.7; số lượt rơi đồ = 1 + phút/60 (tối đa 6), mỗi lượt rơi 60% (+ may mắn). Độ hiếm đồ: 70/22/7/1%, nhiệm vụ cấp cao dịch dần sang hiếm.
- **Thua**: 25% vàng và EXP, không đồ; hero ngã bị thương: nghỉ = thời gian nhiệm vụ × 0.5 (tối đa 2 giờ).
- **Đội**: 1–4 hero; hero đang đi hoặc bị thương không chọn được. Số đội đi cùng lúc: 1 (cấp hội 1), 2 (cấp 5), 3 (cấp 10).
- **Cấp hội**: điểm hội = +1 mỗi nhiệm vụ thắng; lên cấp n→n+1 cần 5 × n điểm.
- **Giải quyết**: khi `now ≥ endAt` và app mở (hoặc bấm nhận), `core` chạy trận với seed = id nhiệm vụ, ghi kết quả vào nhật ký (giữ 30 trận gần nhất), cộng thưởng khi người dùng bấm "Nhận".
- **Đồng hồ**: lưu `lastSeen`; nếu `now < lastSeen` (giờ máy bị chỉnh lùi) thì dùng `lastSeen` làm hiện tại, không lùi tiến trình.
- **Thông báo**: lúc gửi đội, `AlarmManager.setAndAllowWhileIdle` đặt báo ở `endAt` ("Đội X đã về"); huỷ khi đã nhận.

## 5. Tuyển người (quán rượu)

- 4 ứng viên; tự làm mới mỗi 3 giờ (theo kỳ, như bảng nhiệm vụ); làm mới ngay tốn 50 × cấp hội vàng.
- Tỉ lệ độ hiếm theo cấp hội: cấp 1 = 70/25/4.5/0.5%; mỗi cấp chuyển 1% từ Thường sang Hiếm, cứ 5 cấp thêm 1% Sử thi, 10 cấp thêm 0.5% Huyền thoại (giới hạn Thường ≥ 40%).
- Cấp ứng viên = ngẫu nhiên 1 đến max(1, cấp hero cao nhất − 3).
- Giá thuê = 100 × (1/3/10/30 theo độ hiếm) × (1 + cấp/10).
- Đội hình tối đa 12; sa thải hero đang rảnh (đồ đang mặc trả về kho).
- Khởi đầu: 300 vàng + 2 hero Thường (1 Chiến binh, 1 Tu sĩ) cấp 1.

## 6. Lưu trữ

- 1 file `files/save.json`; ghi file tạm `save.json.tmp` → `fsync` → đổi tên (atomic). Lưu sau mỗi thao tác thay đổi trạng thái và ở `onPause`.
- Gốc JSON: `schemaVersion` (= 1), `gold`, `guildExp`, `guildLevel`, `lastSeen`, `nextId`, `heroes[]`, `items[]`, `board` (kỳ + nhiệm vụ), `tavern` (kỳ + ứng viên), `expeditions[]` (questId, heroIds, startAt, endAt, kết quả nếu đã giải), `logs[]` (30 trận gần nhất).
- `SaveCodec` đọc JSON theo `schemaVersion`, bản cũ thì nâng cấp khi đọc. File hỏng không đọc được → giữ bản `save.json.bak` (bản lưu thành công trước đó) và đọc bản đó.

## 7. UI (`app/`)

Dựng bằng code, 1 Activity, thanh tab dưới, giao diện tối/sáng theo hệ thống. Chân dung = vòng tròn chữ cái đầu, màu theo nghề, viền màu độ hiếm. Icon vector Material.

- **Hội**: vàng, cấp hội + thanh điểm, các đội đang đi (thành viên, nhiệm vụ, đếm ngược; đội đã về: nút "Nhận" → hộp thoại kết quả: thắng/thua, vàng, EXP, đồ, lên cấp, nút "Xem trận").
- **Nhiệm vụ**: danh sách nhiệm vụ (vùng, cấp đề xuất, thời gian, thưởng ước tính, đồng hồ đến lượt làm mới bảng) → chạm → chọn 1–4 hero (hiện sức mạnh đội so với đề xuất) → "Gửi đi".
- **Mạo hiểm giả**: lưới đội hình (lọc theo nghề, sắp theo cấp/độ hiếm) → chi tiết: chỉ số cuối, kỹ năng (đã mở/chưa), đặc tính, 6 ô đồ → chạm ô → chọn đồ trong kho phù hợp (so sánh chỉ số) / tháo. Kho đồ: danh sách, bán.
- **Quán rượu**: 4 ứng viên (nghề, độ hiếm, cấp, đặc tính, giá), "Thuê", "Làm mới", đồng hồ làm mới.
- **Nhật ký trận**: danh sách trận gần đây → xem log từng lượt dạng chữ, màu theo phe.
- Tick 1s khi app mở: cập nhật đếm ngược, giải quyết đội đã về.

## 8. Ranh giới module

- `core`: `GameState` (dữ liệu thuần) + `Game` (thao tác: `send`, `claim`, `hire`, `fire`, `equip`, `unequip`, `sell`, `refreshTavern`, `tick(now)`), `Battle`, `Gen` (sinh nhiệm vụ/ứng viên/đồ theo seed), `Stats` (tính chỉ số cuối). Mọi hàm nhận `now` từ ngoài, không tự đọc đồng hồ.
- `app`: `SaveCodec` (GameState ↔ JSON bằng `org.json`), `SaveStore` (đọc/ghi file), `Alarms` + `Notify`, `MainActivity` + các màn hình, `Ui` (hàm dựng view).
