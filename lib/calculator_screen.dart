import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'customer_list_view.dart';

class CalculatorScreen extends StatefulWidget {
  final List<Customer> customers;
  final Customer? initialSelectedCustomer;
  final Function(Customer customer, double amount, String type, String note)? onTransactionSubmit;
  final VoidCallback? onAddCustomerPressed;

  const CalculatorScreen({
    super.key,
    required this.customers,
    this.initialSelectedCustomer,
    this.onTransactionSubmit,
    this.onAddCustomerPressed,
  });

  @override
  State<CalculatorScreen> createState() => _CalculatorScreenState();
}

class _CalculatorScreenState extends State<CalculatorScreen> {
  Customer? _selectedCustomer;
  String _expression = '';
  String _displayValue = '0';
  double _calculatedTotal = 0.0;
  final TextEditingController _noteController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _selectedCustomer = widget.initialSelectedCustomer;
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  void _onKeyPress(String key) {
    HapticFeedback.lightImpact();
    setState(() {
      if (key == 'C') {
        _expression = '';
        _displayValue = '0';
        _calculatedTotal = 0.0;
      } else if (key == '⌫') {
        if (_displayValue.length > 1) {
          _displayValue = _displayValue.substring(0, _displayValue.length - 1);
        } else {
          _displayValue = '0';
        }
        _calculatedTotal = double.tryParse(_displayValue) ?? 0.0;
      } else if (key == '.') {
        if (!_displayValue.contains('.')) {
          _displayValue += '.';
        }
      } else if (key == '00') {
        if (_displayValue != '0') {
          _displayValue += '00';
          _calculatedTotal = double.tryParse(_displayValue) ?? 0.0;
        }
      } else {
        if (_displayValue == '0') {
          _displayValue = key;
        } else {
          _displayValue += key;
        }
        _calculatedTotal = double.tryParse(_displayValue) ?? 0.0;
      }
    });
  }

  void _openCustomerPicker() {
    final theme = Theme.of(context);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: theme.colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return DraggableScrollableSheet(
          initialChildSize: 0.6,
          minChildSize: 0.4,
          maxChildSize: 0.9,
          expand: false,
          builder: (context, scrollController) {
            return Column(
              children: [
                // Drag Handle
                Center(
                  child: Container(
                    margin: const EdgeInsets.only(top: 12, bottom: 8),
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.outlineVariant,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Select Customer',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      if (widget.onAddCustomerPressed != null)
                        TextButton.icon(
                          onPressed: () {
                            Navigator.pop(ctx);
                            widget.onAddCustomerPressed!();
                          },
                          icon: const Icon(Icons.person_add_rounded, size: 18),
                          label: const Text('New'),
                        ),
                    ],
                  ),
                ),
                const Divider(height: 1),
                Expanded(
                  child: widget.customers.isEmpty
                      ? Center(
                          child: Text(
                            'No customers found',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        )
                      : ListView.builder(
                          controller: scrollController,
                          itemCount: widget.customers.length,
                          itemBuilder: (context, index) {
                            final customer = widget.customers[index];
                            final isSelected = _selectedCustomer?.id == customer.id;
                            return ListTile(
                              leading: CircleAvatar(
                                backgroundColor: theme.colorScheme.primaryContainer,
                                child: Text(
                                  customer.initials,
                                  style: TextStyle(
                                    color: theme.colorScheme.onPrimaryContainer,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              title: Text(
                                customer.name,
                                style: TextStyle(
                                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                ),
                              ),
                              subtitle: Text(customer.phone),
                              trailing: isSelected
                                  ? Icon(Icons.check_circle_rounded, color: theme.colorScheme.primary)
                                  : Text(
                                      '₹${customer.netDue.toStringAsFixed(0)}',
                                      style: TextStyle(
                                        fontWeight: FontWeight.bold,
                                        color: customer.netDue > 0
                                            ? const Color(0xFFE53935)
                                            : theme.colorScheme.onSurfaceVariant,
                                      ),
                                    ),
                              onTap: () {
                                setState(() {
                                  _selectedCustomer = customer;
                                });
                                Navigator.pop(ctx);
                              },
                            );
                          },
                        ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  void _submitTransaction(String type) {
    if (_selectedCustomer == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please select a customer first'),
          behavior: SnackBarBehavior.floating,
        ),
      );
      _openCustomerPicker();
      return;
    }

    if (_calculatedTotal <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter a valid amount'),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    if (widget.onTransactionSubmit != null) {
      widget.onTransactionSubmit!(
        _selectedCustomer!,
        _calculatedTotal,
        type,
        _noteController.text.trim(),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        title: const Text(
          'Quick Calculator POS',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        elevation: 0,
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Top Customer Selection Container
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: _buildCustomerSelectionBar(theme),
            ),

            // Amount Display & Note
            Expanded(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 12.0),
                alignment: Alignment.bottomRight,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    if (_expression.isNotEmpty)
                      Text(
                        _expression,
                        style: theme.textTheme.titleMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    const SizedBox(height: 4),
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerRight,
                      child: Text(
                        '₹$_displayValue',
                        style: theme.textTheme.displayLarge?.copyWith(
                          fontWeight: FontWeight.w900,
                          color: theme.colorScheme.onSurface,
                          letterSpacing: -1,
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _noteController,
                      decoration: InputDecoration(
                        hintText: 'Add note / bill item (optional)',
                        hintStyle: TextStyle(
                          fontSize: 13,
                          color: theme.colorScheme.onSurfaceVariant.withOpacity(0.7),
                        ),
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                        filled: true,
                        fillColor: theme.colorScheme.surfaceContainerLow,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none,
                        ),
                        prefixIcon: const Icon(Icons.edit_note_rounded, size: 20),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Action Bar: Received (- / Green) vs Due (+ / Red)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: () => _submitTransaction('RECEIVED'),
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
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: () => _submitTransaction('DUE'),
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

            // Material 3 Keypad Grid
            Container(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
              child: _buildM3Keypad(theme),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCustomerSelectionBar(ThemeData theme) {
    if (_selectedCustomer == null) {
      // Unselected State
      return InkWell(
        onTap: _openCustomerPicker,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLow,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: theme.colorScheme.outlineVariant.withOpacity(0.6),
              width: 1.2,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: theme.colorScheme.primaryContainer.withOpacity(0.6),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.person_add_outlined,
                  color: theme.colorScheme.primary,
                  size: 22,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Select Customer for Ledger',
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.onSurface,
                      ),
                    ),
                    Text(
                      'Tap to assign this transaction',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              Icon(
                Icons.chevron_right_rounded,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ],
          ),
        ),
      );
    }

    // Selected State
    final customer = _selectedCustomer!;
    final isDue = customer.netDue > 0;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: theme.colorScheme.outlineVariant.withOpacity(0.8),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: theme.colorScheme.primaryContainer,
            child: Text(
              customer.initials,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: theme.colorScheme.onPrimaryContainer,
                fontSize: 14,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  customer.name,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  isDue
                      ? 'Net Due: ₹${customer.netDue.toStringAsFixed(0)}'
                      : customer.netDue < 0
                          ? 'Advance: ₹${customer.netDue.abs().toStringAsFixed(0)}'
                          : 'Settled (₹0)',
                  style: theme.textTheme.bodySmall?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: isDue
                        ? const Color(0xFFDC2626)
                        : customer.netDue < 0
                            ? const Color(0xFF16A34A)
                            : theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: _openCustomerPicker,
            style: TextButton.styleFrom(
              visualDensity: VisualDensity.compact,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
            ),
            child: const Text(
              'Change',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildM3Keypad(ThemeData theme) {
    const keypadLayout = [
      ['7', '8', '9', '⌫'],
      ['4', '5', '6', 'C'],
      ['1', '2', '3', '.'],
      ['00', '0', '', ''],
    ];

    return Column(
      children: [
        for (int r = 0; r < 3; r++) ...[
          Row(
            children: [
              for (int c = 0; c < 4; c++) ...[
                Expanded(
                  child: _buildKeypadButton(
                    keypadLayout[r][c],
                    theme,
                    isAction: keypadLayout[r][c] == 'C' || keypadLayout[r][c] == '⌫',
                  ),
                ),
                if (c < 3) const SizedBox(width: 8),
              ],
            ],
          ),
          const SizedBox(height: 8),
        ],
        Row(
          children: [
            Expanded(
              child: _buildKeypadButton('00', theme),
            ),
            const SizedBox(width: 8),
            Expanded(
              flex: 2,
              child: _buildKeypadButton('0', theme),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildKeypadButton('.', theme),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildKeypadButton(String label, ThemeData theme, {bool isAction = false}) {
    if (label.isEmpty) return const SizedBox.shrink();

    final bgColor = isAction
        ? theme.colorScheme.surfaceContainerHighest
        : theme.colorScheme.surfaceContainerHigh;
    final fgColor = isAction
        ? theme.colorScheme.error
        : theme.colorScheme.onSurface;

    return SizedBox(
      height: 54,
      child: FilledButton.tonal(
        onPressed: () => _onKeyPress(label),
        style: FilledButton.styleFrom(
          backgroundColor: bgColor,
          foregroundColor: fgColor,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          padding: EdgeInsets.zero,
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: isAction ? 20 : 22,
            fontWeight: FontWeight.w600,
            color: fgColor,
          ),
        ),
      ),
    );
  }
}
