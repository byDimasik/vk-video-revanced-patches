---
name: pr-lifecycle
description: Use when opening or managing pull requests to handle CodeRabbit code reviews, iterative feedback, inline replies, and automated merges.
---

# PR Lifecycle Management

## Overview

Automates the pull request lifecycle for code reviews. Follows an iterative loop: open PR, await CodeRabbit review, assess feedback, apply fixes, push updates, reply inline, and merge upon approval (up to a maximum of 3 review iterations).

## When to Use

- When opening a new Pull Request or updating an existing Pull Request.
- When automated reviews or comments from CodeRabbit are received.
- Do NOT use for direct pushes to `main` without review.

## Core Workflow

```
Open/Update PR ➔ Await CodeRabbit Review ➔ Assess Findings ➔ Address & Push Fixes ➔ Reply to Comments
                                ▲                                                      │
                                └──────────────── Iteration < 3 ───────────────────────┘
                                                       │
                                          Approved / Max Iterations
                                                       │
                                       Approved ? Merge : Request User Review
```

## Step-by-Step Instructions

1. **Open Pull Request**
   - Push topic branch and create PR targeting `main`.

2. **Await & Fetch CodeRabbit Review**
   - Poll pull request review comments via GitHub REST API (`GET /repos/{owner}/{repo}/pulls/{pr}/comments` and `reviews`).

3. **Assess Review Feedback**
   - Categorize comments into actionable code changes vs. non-actionable/invalid findings.
   - Limit review response iterations to a maximum of **3 loops**.

4. **Address Feedback & Push Updates**
   - Apply required code changes locally.
   - Run verification (`make check`) to ensure 0 errors.
   - Push updated commits to the PR branch.

5. **Reply to Review Comments**
   - Post inline reply comments on resolved threads via GitHub REST API (`POST /repos/{owner}/{repo}/pulls/{pr}/comments/{comment_id}/replies`).

6. **Final Decision**
   - **If Approved by CodeRabbit**: Merge PR into `main` (`POST /repos/{owner}/{repo}/pulls/{pr}/merge`).
   - **If Unresolved after 3 iterations**: Pause and notify user for manual review.

## Common Mistakes

| Mistake | Fix |
|---|---|
| Resolving threads without pushing code fixes | Always push code updates before posting reply comments. |
| Exceeding loop limit | Cap automated review-fix cycles at 3; ask user if still unapproved. |
| Merging unapproved PRs | Only execute merge call if review state is approved or all checks pass cleanly. |
