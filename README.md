# Game Thủ thành
Game Thủ Thành xây dựng công trình, nâng cấp và lính
#Các lệnh trên Github
1. Nhóm lệnh Lưu trữ (Snapshot) - Dùng nhiều nhất:
  **git status**	                Quan trọng nhất. Xem trạng thái các file (file nào vừa sửa, file nào chưa được theo dõi).
  **git add <tên_file>**	        Đưa một file cụ thể vào khu vực chờ (Staging area).
  **git add .**	                  Đưa tất cả file đã sửa vào khu vực chờ. Dùng khi bạn muốn lưu hết mọi thay đổi.
  **git commit -m "mess"**      	Đóng gói các file đang chờ thành một "mốc lịch sử". -m là để viết ghi chú.
  **git commit --amend**	        Sửa lại ghi chú (message) của commit gần nhất (nếu lỡ tay viết sai hoặc quên file).
2. lệnh Nhánh - Dùng để tách biệt công việc của từng người.
  **git branch**	Liệt kê các nhánh đang có trên máy của bạn.
  ****git branch** <tên_nhánh>**	Tạo một nhánh mới (nhưng chưa chuyển sang đó).
  **git checkout <tên_nhánh>**	Chuyển từ nhánh này sang nhánh khác.
  **git checkout -b <tên>**	Combo: Vừa tạo nhánh mới vừa chuyển sang đó luôn (Rất hay dùng).
  **git merge <tên_nhánh>**	Gộp code từ nhánh khác vào nhánh hiện tại. (Ví dụ: Đang đứng ở main, gõ **git merge feature-A** để gộp code của A vào main).
   **git branch -d <tên>**	Xóa nhánh (chỉ xóa được khi nhánh đó đã được gộp xong).
3. Lệnh Đồng bộ - Dùng để giao tiếp giữa máy cá nhân và GitHub (Server).
   **git remote add origin <url>**	Kết nối dự án trên máy với kho chứa trên GitHub (Dùng sau khi git init).
   **git push origin <nhánh>**	Đẩy code từ máy lên GitHub. (Ví dụ: **git push origin main** đẩy lên main).
   **git pull origin <nhánh>**	Lấy code mới nhất từ GitHub về và tự động gộp vào code của bạn.
   **git fetch**	Chỉ tải code mới về để xem, không tự động gộp. (An toàn hơn pull nhưng tốn thêm bước merge).
4. Nhóm lệnh "Quay xe" (Undo & Fix)
   **git checkout .**	       **Nguy hiểm**. Hủy bỏ toàn bộ thay đổi trên các file chưa commit. (Code về trạng thái cũ).
   **git reset HEAD~1**	Hủy commit gần nhất nhưng vẫn giữ lại code đã sửa (để bạn sửa lại rồi commit lại).
   **git reset --hard HEAD**	**Cực nguy hiểm**. Xóa sạch các commit và code chưa lưu, đưa về trạng thái commit trước đó.
   **git stash**	"Cất tạm" code đang làm dở đi nơi khác để chuyển nhánh (vì Git không cho chuyển nhánh khi file đang lộn xộn).
   **git stash pop**	Lấy lại đống code vừa cất tạm ra để làm tiếp.
   **git revert <commit_id>**	Tạo một commit mới để đảo ngược lại tác dụng của một commit cũ (Dùng khi lỡ push code lỗi lên server, muốn sửa sai mà không xóa lịch sử).
5. Nhóm lệnh Kiểm tra (Inspect)
  **git log**	Xem lịch sử các commit (Ai làm gì, vào giờ nào).
  **git log --oneline**	Xem lịch sử dạng rút gọn (mỗi commit 1 dòng).
  **git diff**	Xem chi tiết sự khác biệt của file trước khi add (Bạn đã sửa dòng nào, thêm chữ gì).
  **git blame <tên_file>**	"Tìm thủ phạm". Xem từng dòng code trong file do ai viết và viết lúc nào.




   
Bạn chỉ cần nhớ quy trình 5 bước "thần thánh" này mỗi khi ngồi vào máy làm việc:

1 - git pull (Lấy mới nhất về)

2 - git checkout -b <tên-tính-năng> (Tạo phòng làm việc riêng)

3 - (Code code code...)

4 - git add . + git commit -m "..." (Lưu lại)

5 - git push origin <tên-tính-năng> (Gửi lên cho sếp/nhóm trưởng xem)
