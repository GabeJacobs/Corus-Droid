## Bug Fixes

- When fixing a bug, add a regression test and verify it fails without the fix — run it against the unfixed code first, watch it fail, then apply the fix. A test that never failed proves nothing.
- If the bug can only be confirmed on a device or in the UI, where a unit test would assert a proxy instead of the real behavior, say so, skip the test, and ask me to confirm first.
