# Features — UC Implementation Index

Mỗi UC là một folder riêng gồm 3 files:
- `design.md` — mô tả UC, services, happy/compensating path
- `implementation.md` — plan, thứ tự triển khai, checklist
- `sequence.puml` — sequence diagram

---

| UC          | Folder                       | Services                                             | Status |
|-------------|------------------------------|------------------------------------------------------|--------|
| Customer Registration | [customer-registration/](customer-registration/) | oauth2, identity, customer, notification | Draft  |
| Place Order           | [place-order/](place-order/)                     | order, inventory, payment, fulfillment, notification | Draft  |
