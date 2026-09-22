import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Customer Model
class Customer {
  final String id;
  final String name;
  final String phone;
  final double netDue;
  final DateTime updatedAt;

  const Customer({
    required this.id,
    required this.name,
    required this.phone,
    required this.netDue,
    required this.updatedAt,
  });

  String get initials {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty || parts[0].isEmpty) return '?';
    if (parts.length == 1) return parts[0][0].toUpperCase();
    return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
  }
}

/// Sorting Enum Options
enum CustomerSortOption {
  alphabeticalAZ('Alphabetical (A-Z)'),
  alphabeticalZA('Alphabetical (Z-A)'),
  highestDue('Highest Due First'),
  lastModified('Last Modified');

  final String label;
  const CustomerSortOption(this.label);
}

class CustomerListView extends StatefulWidget {
  final List<Customer> customers;
  final Function(Customer customer)? onCustomerTap;
  final VoidCallback? onAddCustomerPressed;

  const CustomerListView({
    super.key,
    required this.customers,
    this.onCustomerTap,
    this.onAddCustomerPressed,
  });

  @override
  State<CustomerListView> createState() => _CustomerListViewState();
}

class _CustomerListViewState extends State<CustomerListView> {
  CustomerSortOption _sortOption = CustomerSortOption.alphabeticalAZ;
  final ScrollController _scrollController = ScrollController();

  // Alphabet scrubber state
  String? _scrubLetter;
  bool _isScrubbing = false;
  Timer? _scrubTimer;

  static const List<String> _alphabet = [
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '#'
  ];

  @override
  void dispose() {
    _scrollController.dispose();
    _scrubTimer?.cancel();
    super.dispose();
  }

  bool get _isAlphabeticalSort =>
      _sortOption == CustomerSortOption.alphabeticalAZ ||
      _sortOption == CustomerSortOption.alphabeticalZA;

  List<Customer> get _sortedCustomers {
    final list = List<Customer>.from(widget.customers);
    switch (_sortOption) {
      case CustomerSortOption.alphabeticalAZ:
        list.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
        break;
      case CustomerSortOption.alphabeticalZA:
        list.sort((a, b) => b.name.toLowerCase().compareTo(a.name.toLowerCase()));
        break;
      case CustomerSortOption.highestDue:
        list.sort((a, b) => b.netDue.compareTo(a.netDue));
        break;
      case CustomerSortOption.lastModified:
        list.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
        break;
    }
    return list;
  }

  void _onScrubUpdate(Offset localPosition, double totalHeight) {
    if (totalHeight <= 0) return;
    
    final sorted = _sortedCustomers;
    if (sorted.isEmpty) return;

    final stepHeight = totalHeight / _alphabet.length;
    final index = (localPosition.dy / stepHeight).clamp(0, _alphabet.length - 1).floor();
    final letter = _alphabet[index];

    if (_scrubLetter != letter) {
      HapticFeedback.selectionClick();
      setState(() {
        _scrubLetter = letter;
        _isScrubbing = true;
      });

      _scrubTimer?.cancel();
      _scrubTimer = Timer(const Duration(milliseconds: 1200), () {
        if (mounted) {
          setState(() {
            _isScrubbing = false;
          });
        }
      });

      // Find first customer matching this letter
      final targetIndex = _findCustomerIndexForLetter(sorted, letter);
      if (targetIndex != -1) {
        // Approximate card height (padding + card = ~88dp)
        const double estimatedItemHeight = 88.0;
        final targetOffset = targetIndex * estimatedItemHeight;
        if (_scrollController.hasClients) {
          _scrollController.jumpTo(
            targetOffset.clamp(0.0, _scrollController.position.maxScrollExtent),
          );
        }
      }
    }
  }

  int _findCustomerIndexForLetter(List<Customer> sortedList, String letter) {
    if (letter == '#') {
      return sortedList.indexWhere((c) {
        final firstChar = c.name.trim().isEmpty ? '' : c.name.trim()[0].toUpperCase();
        return !RegExp(r'^[A-Z]$').hasMatch(firstChar);
      });
    }

    return sortedList.indexWhere((c) {
      final firstChar = c.name.trim().isEmpty ? '' : c.name.trim()[0].toUpperCase();
      return firstChar == letter;
    });
  }

  String _formatRelativeTime(DateTime dateTime) {
    final difference = DateTime.now().difference(dateTime);
    if (difference.inSeconds < 60) return 'Just now';
    if (difference.inMinutes < 60) return '${difference.inMinutes}m ago';
    if (difference.inHours < 24) return '${difference.inHours}h ago';
    if (difference.inDays < 7) return '${difference.inDays}d ago';
    return '${dateTime.day}/${dateTime.month}/${dateTime.year}';
  }

  String _formatCurrency(double amount) {
    return '₹${amount.abs().toStringAsFixed(0)}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final sortedList = _sortedCustomers;

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        title: const Text(
          'Customers',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        elevation: 0,
        actions: [
          PopupMenuButton<CustomerSortOption>(
            icon: const Icon(Icons.sort_rounded),
            tooltip: 'Sort Customers',
            initialValue: _sortOption,
            onSelected: (option) {
              setState(() {
                _sortOption = option;
              });
            },
            itemBuilder: (context) => CustomerSortOption.values.map((option) {
              return PopupMenuItem<CustomerSortOption>(
                value: option,
                child: Row(
                  children: [
                    Icon(
                      _sortOption == option
                          ? Icons.radio_button_checked_rounded
                          : Icons.radio_button_unchecked_rounded,
                      size: 18,
                      color: _sortOption == option
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 12),
                    Text(option.label),
                  ],
                ),
              );
            }).toList(),
          ),
        ],
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: widget.onAddCustomerPressed,
        icon: const Icon(Icons.person_add_rounded),
        label: const Text('New Customer'),
        elevation: 3,
      ),
      body: Stack(
        children: [
          // Customer List
          if (sortedList.isEmpty)
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.people_outline_rounded,
                    size: 64,
                    color: theme.colorScheme.onSurfaceVariant.withOpacity(0.5),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'No customers found',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            )
          else
            ListView.builder(
              controller: _scrollController,
              padding: EdgeInsets.only(
                left: 16,
                top: 12,
                right: _isAlphabeticalSort ? 36 : 16, // Extra right padding for A-Z strip
                bottom: 88, // Standard padding to prevent FAB overlap
              ),
              itemCount: sortedList.length,
              itemBuilder: (context, index) {
                final customer = sortedList[index];
                return _buildCustomerCard(context, customer);
              },
            ),

          // Alphabet Scroller Strip
          if (_isAlphabeticalSort && sortedList.isNotEmpty)
            Positioned(
              right: 2,
              top: 8,
              bottom: 88,
              width: 28,
              child: LayoutBuilder(
                builder: (context, constraints) {
                  return GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onVerticalDragStart: (details) => _onScrubUpdate(
                      details.localPosition,
                      constraints.maxHeight,
                    ),
                    onVerticalDragUpdate: (details) => _onScrubUpdate(
                      details.localPosition,
                      constraints.maxHeight,
                    ),
                    onTapDown: (details) => _onScrubUpdate(
                      details.localPosition,
                      constraints.maxHeight,
                    ),
                    child: Container(
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: _alphabet.map((letter) {
                          final isSelected = _scrubLetter == letter && _isScrubbing;
                          return Text(
                            letter,
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: isSelected ? FontWeight.black : FontWeight.w600,
                              color: isSelected
                                  ? theme.colorScheme.primary
                                  : theme.colorScheme.onSurfaceVariant.withOpacity(0.7),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                  );
                },
              ),
            ),

          // Temporary Centered Floating Callout Chip while Scrubbing
          if (_isScrubbing && _scrubLetter != null)
            Center(
              child: AnimatedOpacity(
                duration: const Duration(milliseconds: 150),
                opacity: _isScrubbing ? 1.0 : 0.0,
                child: Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primaryContainer,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.2),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    _scrubLetter!,
                    style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.onPrimaryContainer,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildCustomerCard(BuildContext context, Customer customer) {
    final theme = Theme.of(context);
    final isDue = customer.netDue > 0;
    final isAdvance = customer.netDue < 0;

    // Trailing Due Color Logic
    Color dueTextColor;
    String dueLabel;

    if (isDue) {
      dueTextColor = const Color(0xFFE53935); // Semantic Muted Red
      dueLabel = _formatCurrency(customer.netDue);
    } else if (isAdvance) {
      dueTextColor = const Color(0xFF2E7D32); // Semantic Green
      dueLabel = '${_formatCurrency(customer.netDue)} (Adv)';
    } else {
      dueTextColor = const Color(0xFF64748B); // Slate / Grey
      dueLabel = '₹0';
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: theme.colorScheme.outlineVariant.withOpacity(0.5),
          width: 1,
        ),
      ),
      color: theme.colorScheme.surfaceContainerLow,
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        onTap: () {
          if (widget.onCustomerTap != null) {
            widget.onCustomerTap!(customer);
          }
        },
        leading: CircleAvatar(
          radius: 22,
          backgroundColor: theme.colorScheme.primaryContainer,
          child: Text(
            customer.initials,
            style: TextStyle(
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onPrimaryContainer,
              fontSize: 15,
            ),
          ),
        ),
        title: Text(
          customer.name,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.bold,
          ),
          maxLines: 1,
          overflow: TextSpanOverflow?.clip != null ? TextOverflow.ellipsis : TextOverflow.ellipsis,
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4.0),
          child: Text(
            '${customer.phone} • ${_formatRelativeTime(customer.updatedAt)}',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        trailing: Text(
          dueLabel,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.black,
            color: dueTextColor,
          ),
        ),
      ),
    );
  }
}
