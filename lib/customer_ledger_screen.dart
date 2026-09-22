import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'customer_list_view.dart';

/// Transaction Model
class TransactionItem {
  final String id;
  final String customerId;
  final double amount;
  final String type; // 'DUE' (gave/credit) or 'RECEIVED' (got/payment)
  final String note;
  final DateTime timestamp;
  final double runningBalance;

  const TransactionItem({
    required this.id,
    required this.customerId,
    required this.amount,
    required this.type,
    required this.note,
    required this.timestamp,
    required this.runningBalance,
  });

  bool get isDue => type == 'DUE';
}

class CustomerLedgerScreen extends StatelessWidget {
  final Customer customer;
  final List<TransactionItem> transactions;
  final Function(TransactionItem transaction)? onTransactionTap;
  final Function(String type)? onAddTransactionPressed;
  final VoidCallback? onSharePdfPressed;
  final VoidCallback? onShareQrPressed;
  final VoidCallback? onEditCustomerPressed;
  final VoidCallback? onDeleteCustomerPressed;

  const CustomerLedgerScreen({
    super.key,
    required this.customer,
    required this.transactions,
    this.onTransactionTap,
    this.onAddTransactionPressed,
    this.onSharePdfPressed,
    this.onShareQrPressed,
    this.onEditCustomerPressed,
    this.onDeleteCustomerPressed,
  });

  String _formatCurrency(double amount) {
    return '₹${amount.abs().toStringAsFixed(0)}';
  }

  String _formatTime(DateTime dateTime) {
    final hour = dateTime.hour == 0 ? 12 : (dateTime.hour > 12 ? dateTime.hour - 12 : dateTime.hour);
    final minute = dateTime.minute.toString().padLeft(2, '0');
    final period = dateTime.hour >= 12 ? 'PM' : 'AM';
    return '$hour:$minute $period';
  }

  String _getDateGroupHeader(DateTime date) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final yesterday = today.subtract(const Duration(days: 1));
    final itemDate = DateTime(date.year, date.month, date.day);

    if (itemDate == today) {
      return 'Today';
    } else if (itemDate == yesterday) {
      return 'Yesterday';
    } else if (today.difference(itemDate).inDays < 7) {
      const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
      return weekdays[date.weekday - 1];
    } else {
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return '${date.day} ${months[date.month - 1]} ${date.year}';
    }
  }

  Map<String, List<TransactionItem>> _groupTransactionsByDate(List<TransactionItem> list) {
    final Map<String, List<TransactionItem>> grouped = {};
    // Ensure chronological order descending
    final sorted = List<TransactionItem>.from(list)
      ..sort((a, b) => b.timestamp.compareTo(a.timestamp));

    for (final tx in sorted) {
      final header = _getDateGroupHeader(tx.timestamp);
      grouped.putIfAbsent(header, () => []).add(tx);
    }
    return grouped;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final groupedTx = _groupTransactionsByDate(transactions);
    final isNetDue = customer.netDue > 0;
    final isNetAdvance = customer.netDue < 0;

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: theme.colorScheme.surface,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              customer.name,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            if (customer.phone.isNotEmpty)
              Text(
                customer.phone,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
          ],
        ),
        actions: [
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert_rounded),
            tooltip: 'Options',
            onSelected: (value) {
              HapticFeedback.lightImpact();
              switch (value) {
                case 'share_pdf':
                  onSharePdfPressed?.call();
                  break;
                case 'share_qr':
                  onShareQrPressed?.call();
                  break;
                case 'edit_customer':
                  onEditCustomerPressed?.call();
                  break;
                case 'delete_customer':
                  onDeleteCustomerPressed?.call();
                  break;
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'share_pdf',
                child: Row(
                  children: [
                    Icon(Icons.picture_as_pdf_outlined, size: 20),
                    SizedBox(width: 12),
                    Text('Statement PDF'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'share_qr',
                child: Row(
                  children: [
                    Icon(Icons.qr_code_rounded, size: 20),
                    SizedBox(width: 12),
                    Text('UPI Payment QR'),
                  ],
                ),
              ),
              const PopupMenuDivider(),
              const PopupMenuItem(
                value: 'edit_customer',
                child: Row(
                  children: [
                    Icon(Icons.edit_outlined, size: 20),
                    SizedBox(width: 12),
                    Text('Edit Customer'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: 'delete_customer',
                child: Row(
                  children: [
                    Icon(Icons.delete_outline_rounded, size: 20, color: theme.colorScheme.error),
                    const SizedBox(width: 12),
                    Text('Delete Account', style: TextStyle(color: theme.colorScheme.error)),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          // Net Balance Summary Header Card
          Container(
            margin: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            decoration: BoxDecoration(
              color: isNetDue
                  ? const Color(0xFFFEF2F2)
                  : isNetAdvance
                      ? const Color(0xFFF0FDF4)
                      : theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: isNetDue
                    ? const Color(0xFFFCA5A5).withOpacity(0.5)
                    : isNetAdvance
                        ? const Color(0xFF86EFAC).withOpacity(0.5)
                        : theme.colorScheme.outlineVariant.withOpacity(0.5),
              ),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isNetDue
                          ? 'TOTAL AMOUNT DUE'
                          : isNetAdvance
                              ? 'ADVANCE BALANCE'
                              : 'ACCOUNT STATUS',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.8,
                        color: isNetDue
                            ? const Color(0xFF991B1B)
                            : isNetAdvance
                                ? const Color(0xFF166534)
                                : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _formatCurrency(customer.netDue),
                      style: TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -0.5,
                        color: isNetDue
                            ? const Color(0xFFDC2626)
                            : isNetAdvance
                                ? const Color(0xFF16A34A)
                                : theme.colorScheme.onSurface,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: isNetDue
                        ? const Color(0xFFFEE2E2)
                        : isNetAdvance
                            ? const Color(0xFFDCFCE7)
                            : theme.colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    isNetDue
                        ? 'Due'
                        : isNetAdvance
                            ? 'Advance'
                            : 'Settled',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: isNetDue
                          ? const Color(0xFFB91C1C)
                          : isNetAdvance
                              ? const Color(0xFF15803D)
                              : theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ),
          ),

          // Grouped Transaction History List
          Expanded(
            child: transactions.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.receipt_long_outlined,
                          size: 56,
                          color: theme.colorScheme.onSurfaceVariant.withOpacity(0.5),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          'No transactions yet',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Record your first entry using the buttons below',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant.withOpacity(0.7),
                          ),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    itemCount: groupedTx.keys.length,
                    itemBuilder: (context, groupIndex) {
                      final header = groupedTx.keys.elementAt(groupIndex);
                      final items = groupedTx[header]!;

                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // Section Header
                          Padding(
                            padding: const EdgeInsets.only(top: 14, bottom: 8, left: 4),
                            child: Text(
                              header.toUpperCase(),
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 0.8,
                                color: theme.colorScheme.onSurfaceVariant.withOpacity(0.8),
                              ),
                            ),
                          ),

                          // Transaction Cards for this Date
                          ...items.map((tx) => _buildTransactionCard(context, tx)),
                        ],
                      );
                    },
                  ),
          ),

          // Bottom Action Bar: Fast Entry Buttons
          Container(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  offset: const Offset(0, -2),
                  blurRadius: 8,
                ),
              ],
            ),
            child: Row(
              children: [
                // Received Payment Button (- / Green)
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () {
                      HapticFeedback.mediumImpact();
                      onAddTransactionPressed?.call('RECEIVED');
                    },
                    icon: const Icon(Icons.arrow_downward_rounded, size: 18),
                    label: const Text(
                      '- RECEIVED',
                      style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 0.5),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF16A34A),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      elevation: 0,
                    ),
                  ),
                ),
                const SizedBox(width: 12),

                // Due / Credit Button (+ / Red)
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () {
                      HapticFeedback.mediumImpact();
                      onAddTransactionPressed?.call('DUE');
                    },
                    icon: const Icon(Icons.arrow_upward_rounded, size: 18),
                    label: const Text(
                      '+ DUE',
                      style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 0.5),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFDC2626),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      elevation: 0,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTransactionCard(BuildContext context, TransactionItem tx) {
    final theme = Theme.of(context);
    final isDue = tx.isDue;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: theme.colorScheme.outlineVariant.withOpacity(0.4),
          width: 1,
        ),
      ),
      color: theme.colorScheme.surfaceContainerLow,
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () {
          HapticFeedback.lightImpact();
          onTransactionTap?.call(tx);
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              // Direction Icon
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: isDue
                      ? const Color(0xFFFEE2E2)
                      : const Color(0xFFDCFCE7),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  isDue ? Icons.arrow_upward_rounded : Icons.arrow_downward_rounded,
                  color: isDue ? const Color(0xFFDC2626) : const Color(0xFF16A34A),
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),

              // Note & Time
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      tx.note.isNotEmpty ? tx.note : (isDue ? 'Goods / Credit Given' : 'Payment Received'),
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.onSurface,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _formatTime(tx.timestamp),
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),

              // Amount & Running Balance aligned on the right
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${isDue ? '+' : '-'}${_formatCurrency(tx.amount)}',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w900,
                      color: isDue ? const Color(0xFFDC2626) : const Color(0xFF16A34A),
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Bal: ${_formatCurrency(tx.runningBalance)}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: theme.colorScheme.onSurfaceVariant.withOpacity(0.8),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
