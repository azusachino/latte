import 'package:flutter/material.dart';

import '../../domain/explore_state.dart';
import '../../domain/post.dart';
import 'explore_controller.dart';

class SearchView extends StatefulWidget {
  const SearchView({
    required this.controller,
    this.showStatus = true,
    this.showBar = true,
    super.key,
  });

  final ExploreController controller;
  final bool showStatus;
  final bool showBar;

  @override
  SearchViewState createState() => SearchViewState();
}

class SearchViewState extends State<SearchView> {
  late final SearchController _searchController;

  void open() => _searchController.openView();

  @override
  void initState() {
    super.initState();
    _searchController = SearchController()..addListener(_textChanged);
  }

  @override
  void dispose() {
    _searchController
      ..removeListener(_textChanged)
      ..dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final state = widget.controller.state;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SearchAnchor(
              searchController: _searchController,
              viewHintText: 'Search tags',
              viewLeading: IconButton(
                onPressed: () => _searchController.closeView(null),
                icon: const Icon(Icons.arrow_back),
                tooltip: 'Close search',
              ),
              viewTrailing: [_clearButton()],
              viewOnSubmitted: _submit,
              builder: (context, controller) => widget.showBar
                  ? SearchBar(
                      controller: controller,
                      hintText: 'Search tags',
                      leading: const Icon(Icons.search),
                      trailing: [_clearButton()],
                      onTap: controller.openView,
                      onChanged: (_) => controller.openView(),
                      onSubmitted: _submit,
                    )
                  : const SizedBox.shrink(),
              suggestionsBuilder: (context, controller) => _suggestions(state),
            ),
            if (widget.showStatus) _SearchStatus(state: state),
          ],
        );
      },
    );
  }

  Iterable<Widget> _suggestions(ExploreState state) {
    if (state.status == ExploreStatus.noResults) {
      return const [ListTile(title: Text('No matches'))];
    }
    if (state.status == ExploreStatus.failure && state.failure != null) {
      return [ListTile(title: Text(state.failure!.message))];
    }
    return const [];
  }

  Widget _clearButton() => IconButton(
    onPressed: _searchController.text.isEmpty ? null : _clear,
    icon: const Icon(Icons.clear),
    tooltip: 'Clear search',
    constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
  );

  Future<void> _submit(String expression) async {
    await widget.controller.search(expression);
    if (!mounted) return;
    if (_searchController.isOpen) {
      _searchController.closeView(expression.trim());
    }
    setState(() {});
  }

  Future<void> _clear() async {
    _searchController.clear();
    await widget.controller.clearSearch();
    if (!mounted) return;
    if (_searchController.isOpen) _searchController.closeView('');
    setState(() {});
  }

  void _textChanged() => setState(() {});
}

class _SearchStatus extends StatelessWidget {
  const _SearchStatus({required this.state});

  final ExploreState state;

  @override
  Widget build(BuildContext context) {
    final message = switch (state.status) {
      ExploreStatus.noResults => 'No matches',
      ExploreStatus.failure
          when state.query?.source == PostQuerySource.tagSearch =>
        state.failure?.message ?? 'Yande.re is unavailable',
      _ => null,
    };
    if (message == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Semantics(
        liveRegion: true,
        child: Text(message, textAlign: TextAlign.center),
      ),
    );
  }
}
