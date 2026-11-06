SELECT
    o.account_uuid,
    COUNT(o.invoice_id) AS total_invoices,
    SUM(o.amount_cents) AS lifetime_value_cents
FROM billing.invoices o
WHERE o.status = 'SETTLED'
GROUP BY o.account_uuid
HAVING SUM(o.amount_cents) > 50000;
