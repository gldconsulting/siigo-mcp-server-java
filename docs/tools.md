# Inventario de herramientas

SDK: `co.gldconsulting:siigo-api-sdk:1.0.0`. 81 herramientas; se omiten sobrecargas default duplicadas y autenticación.

Los argumentos se descubren mediante `tools/list` del protocolo. Las escrituras usan el SDK y requieren intención/aprobación en el host.

## products

- `siigo_create_product`
- `siigo_delete_product`
- `siigo_get_all_products`
- `siigo_get_product`
- `siigo_get_products`
- `siigo_update_product`

## customers

- `siigo_create_customer`
- `siigo_get_all_customers`
- `siigo_get_customer`
- `siigo_get_customers`
- `siigo_update_customer`

## invoices

- `siigo_annul_invoice`
- `siigo_create_invoice`
- `siigo_create_invoice_batch`
- `siigo_delete_invoice`
- `siigo_get_all_invoices`
- `siigo_get_electronic_errors`
- `siigo_get_invoice`
- `siigo_get_invoice_pdf`
- `siigo_get_invoice_xml`
- `siigo_get_invoices`
- `siigo_send_invoice_by_email`
- `siigo_update_invoice`

## quotations

- `siigo_create_quotation`
- `siigo_delete_quotation`
- `siigo_get_all_quotations`
- `siigo_get_quotation`
- `siigo_get_quotations`
- `siigo_update_quotation`

## credit_notes

- `siigo_create_credit_note`
- `siigo_get_all_credit_notes`
- `siigo_get_credit_note`
- `siigo_get_credit_note_pdf`
- `siigo_get_credit_notes`

## purchases

- `siigo_create_purchase`
- `siigo_delete_purchase`
- `siigo_get_all_purchases`
- `siigo_get_purchase`
- `siigo_get_purchases`
- `siigo_get_purchases_by_date`
- `siigo_update_purchase`

## journals

- `siigo_create_journal`
- `siigo_get_all_journals`
- `siigo_get_journal`
- `siigo_get_journals`
- `siigo_get_journals_by_date`

## vouchers

- `siigo_create_misc_income`
- `siigo_create_voucher`
- `siigo_get_all_vouchers`
- `siigo_get_voucher`
- `siigo_get_vouchers`

## support_documents

- `siigo_create_support_document`
- `siigo_delete_support_document`
- `siigo_get_support_document`
- `siigo_update_support_document`

## payment_receipts

- `siigo_create_payment_receipt`
- `siigo_delete_payment_receipt`
- `siigo_get_payment_receipt`
- `siigo_get_payment_receipts`

## inventory_categories

- `siigo_create_account_group`
- `siigo_get_account_groups`
- `siigo_update_account_group`

## catalogs

- `siigo_get_all_users`
- `siigo_get_cost_centers`
- `siigo_get_document_types`
- `siigo_get_expenses`
- `siigo_get_fixed_assets`
- `siigo_get_misc_incomes`
- `siigo_get_payment_types`
- `siigo_get_price_lists`
- `siigo_get_taxes`
- `siigo_get_users`
- `siigo_get_warehouses`

## reports

- `siigo_get_accounts_payable`
- `siigo_get_test_balance`
- `siigo_get_third_party_test_balance`

## webhooks

- `siigo_create_webhook`
- `siigo_delete_webhook`
- `siigo_get_all_webhooks`
- `siigo_get_webhooks`
- `siigo_update_webhook`
