# Contributing notes: JSONPath proposal for Jakarta JSON Processing

Working notes for the JSONPath (RFC 9535) proposal across three upstream projects: Jakarta JSON Processing
(`jakartaee/jsonp-api`), Eclipse Parsson and Apache Johnzon. This is a checklist for the contributors of this
fork, not legal advice. Sources were read on 2026-10-18; the Eclipse text is the Project Handbook
"Current as of 2026-09-25", so re-check before an upstream PR.

Status key: **OPEN** = needs a decision or action from Christoph before anything goes upstream.

## 1. Eclipse Contributor Agreement (jsonp-api, Parsson)

Rules:

- Anyone who is not a committer must have an Eclipse Foundation account and a signed ECA on file
  ([Handbook: ECA](https://www.eclipse.org/projects/handbook/#contributing-eca),
  [Handbook: Git commit records](https://www.eclipse.org/projects/handbook/#resources-commit)).
- The commit **Author** must carry the real (legal) name and an email address that **matches the address the
  Eclipse Foundation has on file, case-sensitive**
  ([Handbook: Git commit records](https://www.eclipse.org/projects/handbook/#resources-commit)).
  Additional authors go in `Co-authored-by:` / `Also-by: Name <email>` footers with a real, valid email.
- Committers must not merge unless the contributor has a valid ECA; GitHub PRs get an automated ECA status check
  ([Committer Due Diligence Guidelines](https://www.eclipse.org/legal/committer-guidelines/)).
- `jsonp-api`'s own [CONTRIBUTING.md](../CONTRIBUTING.md) additionally asks non-committers for a
  `Signed-off-by` footer (`git commit -s`), i.e. the Developer Certificate of Origin.
- Contributions must arrive through Eclipse channels (GitHub PR), never privately by email
  ([Committer Due Diligence Guidelines](https://www.eclipse.org/legal/committer-guidelines/)).

Consequences for us:

- Until the question below is answered, all commits in the forks use
  `christoph.sens.consulting@gmail.com` as author. These commits are **not** upstream-ready.
- **OPEN:** which email is registered on Christoph's Eclipse account, and is the ECA signed? Before the first
  upstream PR, the commits must be rewritten (`git rebase --exec 'git commit --amend --reset-author --no-edit -s'`
  after setting `user.email`) or the PR branch re-created with the right author. Check the exact-match rule
  including letter case. Add the GitHub ID to the Eclipse account as well.
- Every commit gets `Signed-off-by` for jsonp-api PRs. The `Co-Authored-By: Claude ...` trailer stays (see section 4),
  but the AI is not a "legal author" with an email: the Eclipse ECA check looks at the human author.
- Keep commit summaries at 72 characters or less.

## 2. Apache Johnzon: is an ICLA needed?

What the ASF says ([contributor agreements](https://www.apache.org/licenses/contributor-agreements.html)):

- "Small contributions" are covered by clause 5 of the Apache License 2.0 (inbound = outbound), no paperwork.
- Committers/project maintainers, and contributors of "any large contributions", must file an **ICLA**. The
  page gives **no numeric threshold**.
- A Software Grant Agreement (SGA) is for donating an existing body of software or documentation, normally
  after PMC/Incubator approval.
- Forms go by email to secretary@apache.org; hand signature, GPG detached signature or DocuSign.

Johnzon itself: the GitHub repo ([apache/johnzon](https://github.com/apache/johnzon)) has no CONTRIBUTING.md;
[johnzon.apache.org](https://johnzon.apache.org/) and its [source page](https://johnzon.apache.org/scm.html)
say nothing about ICLAs or patches. So the ASF-wide rule applies and nothing project-specific narrows it.

Assessment (judgement, not an ASF statement): a full JSONPath implementation (tens of thousands of lines, a new
module with a public API) is a "large contribution". **File an ICLA before opening the PR**, and discuss the
shape with the Johnzon PMC on dev@johnzon.apache.org first (the PMC may ask for a Software Grant because the code pre-exists as Treetrail; the ASF page does not say when). Cheap, avoids a stalled PR.

- **OPEN:** Christoph files an ICLA (his decision whether under his own name or with a CCLA if an employer
  claims rights; as a consultant, check the client contracts for IP clauses).
- ICLA clause "original creation": see the Generative tooling policy in section 4.

## 3. Eclipse IP review for large contributions (jsonp-api, Parsson)

[Handbook: IP, Project Content](https://www.eclipse.org/projects/handbook/#ip-project-content) and the
[Committer Due Diligence Guidelines](https://www.eclipse.org/legal/committer-guidelines/):

Contributions from non-committers need further investigation, with a **project committer engaging the Eclipse IP
Team before the contribution is merged**, if any of these is true:

1. the content was copied or is based on copied content (not 100% authored by the submitter);
2. the licence differs from the project licence;
3. it contains cryptography;
4. it contains **more than 1,000 lines** (code, documentation, configuration).

A project cannot release content whose review is not complete. Small fixes under the Eclipse Terms of Use need no
IP Team review.

Consequences for us:

- The JSONPath API + spec chapter + TCK + Parsson implementation is far beyond 1,000 lines: **IP Team review is
  mandatory** for the jsonp-api and Parsson PRs. Merging needs a committer to start it. Plan: ask on the
  jsonp-dev list / jsonp-api#448 who sponsors it, and split the PRs into reviewable pieces (API, impl, TCK, spec)
  so that the IP review can run per piece.
- Condition 2 applies too: **Treetrail is Apache-2.0**, the project licence of jsonp-api is
  EPL-2.0 (OR GPL-2.0 WITH Classpath-exception-2.0); Parsson is EPL-2.0. Because Christoph is the sole author, he can
  contribute his own code under the Eclipse licence (the ECA is the grant), but the IP Team will want this stated
  clearly, plus the provenance of every non-original piece:
  - third-party test data (e.g. the RFC 9535 compliance test suite, its licence) used in the TCK;
  - any code copied from other JSONPath libraries; none should be.
  - **OPEN:** inventory of third-party material in Treetrail (tests, fixtures, dependencies) before the first PR.
- Dependencies: any third-party runtime dependency added by the port needs the third-party process
  ([Handbook: Third-party content](https://www.eclipse.org/projects/handbook/#ip-third-party)); the goal
  is zero new dependencies.
- For Johnzon (Apache), the Eclipse process does not apply; the Apache rules in section 2 and 4 do.

## 4. Generative AI: Eclipse and Apache, and how we comply

### Eclipse

[Generative AI Usage Guidelines for Eclipse Committers v1.0, April 2024](https://www.eclipse.org/projects/handbook/#genai)
(also reachable via <https://www.eclipse.org/projects/guidelines/genai>) and the
[Disclosure section](https://www.eclipse.org/projects/handbook/#genai-disclosure):

- Be transparent; keep records of which AI platforms were used. Disclosure: "reasonable effort".
- Verify accuracy and vet output through the normal processes: testing, IP due diligence, security.
- Check the provider's terms of use and what happens to inputs; do not feed confidential or personal data.
- Use plagiarism/similarity tools where the platform offers them.
- Legal view in the guidelines: purely AI-generated content is probably not copyrightable; human curation can
  restore copyright for parts. For files **largely or entirely AI-generated**, the handbook proposes a header
  with the project licence **plus an AI disclosure** (AI portions under CC0-1.0, SPDX
  `EPL-2.0 AND CC0-1.0`) and an `Assisted-by: [Provider] [Model] ([Version])` trailer in the commit.
  The handbook says this "may change" and is not legal advice.

### Apache

[ASF Generative Tooling guidance](https://www.apache.org/legal/generative-tooling.html) (checked 2026-10-18):

- Allowed if the contributor ensures (1) the tool's terms do not restrict the output in a way that conflicts with the
  Open Source Definition and give enough rights to license it to the ASF; (2) the output is non-copyrightable or has no
  third-party material or such material is used with permission and licence compliance; (3) reasonable certainty
  on that, where the tool offers similarity information.
- Recommended, not required: record the tool in the commit message, `Generated-by:` or `Co-authored-by:`.
- The ICLA's "original creation" statement still applies to the contributor; the policy says the "not your
  original work" route is not practical for AI output. Public texts (announcements, release notes) written by AI
  should be labelled unless a human reviewed and takes responsibility.

### Our compliance rules for this project

1. **Disclosure everywhere.** Every commit made with AI help carries
   `Co-Authored-By: <model> <noreply@anthropic.com>` (as set up for this project). For commits going upstream to
   Eclipse, add the Eclipse-preferred `Assisted-by: Anthropic <model> (<version/ID>)` line next to it; whichever
   form the committers prefer, keep both machine-readable. Every upstream PR description states that the work is
   AI-assisted, which tool, and that a human reviewed it. This matches the stated goal of disclosing AI use.
2. **Human review before every merge.** Christoph (or another human) reviews each PR diff; AI-written code is
   never pushed directly to an upstream repository. The PR description says "reviewed and verified by <name>".
3. **Tests and TCK as vetting.** Per the Eclipse guidance on vetting: RFC 9535 compliance suite, TCK, unit tests
   and CI (see the `jsonpath-ci.yml` workflows) must be green; security review stays a human step.
4. **Provenance check.** Run a similarity/licence scan (e.g. ScanCode or the platform's code-reference filter)
   on the generated code before the IP request, and record the result for the IP Team. Do not paste code from other
   JSONPath libraries into prompts or into the code.
5. **Headers.** Files that are largely AI-generated get the header form proposed by the Eclipse handbook (licence +
   AI disclosure) **if the committers/IP Team want it**; **OPEN:** ask the IP Team/committers on #448 or the dev list
   whether they want the CC0 split header or a simple project-licence header, because the CC0 dedication changes
   the licence terms of the file. Until answered, use the plain project header (Eclipse: `SPDX-License-Identifier:
   EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0`; Johnzon: Apache header).
6. **Tool terms and input hygiene.** Read the provider's terms for the plan in use (output rights, restrictions) and record the result here; **OPEN**, not yet checked. No customer data or secrets in prompts.
7. **Communication.** Replies in upstream threads are written by Christoph in his own voice (see the project
   decisions); AI drafts are fine, AI-sounding text is not.
8. **Copyright headers.** Authorship line names the human legal entity (Christoph Sens or the consulting entity),
   not the AI.

## 5. Checklist before the first upstream PR

- [ ] Eclipse account email known, ECA signed, commits re-authored with that email and `Signed-off-by`
- [ ] ICLA filed with the ASF (Johnzon)
- [ ] Committer sponsor found and IP Team review requested (jsonp-api, Parsson); PRs split into reviewable parts
- [ ] Third-party inventory (test data, dependencies) done; no new dependencies
- [ ] Similarity/licence scan done, results saved
- [ ] AI disclosure in commits and PR text; header style agreed
- [ ] Human review of the full diff
- [ ] CI green (these forks' `jsonpath-ci.yml` baseline plus new tests)

## Sources

- Eclipse Project Handbook (current as of 2026-09-25): <https://www.eclipse.org/projects/handbook/>
  (`#contributing-eca`, `#resources-commit`, `#ip-project-content`, `#ip-third-party`, `#genai`, `#genai-disclosure`)
- Eclipse Committer Due Diligence Guidelines: <https://www.eclipse.org/legal/committer-guidelines/>
- Eclipse Contributor Agreement: <https://www.eclipse.org/legal/ECA.php>
- jsonp-api CONTRIBUTING.md: [../CONTRIBUTING.md](../CONTRIBUTING.md)
- Apache contributor agreements: <https://www.apache.org/licenses/contributor-agreements.html>
- Apache generative tooling guidance: <https://www.apache.org/legal/generative-tooling.html>
- Johnzon: <https://github.com/apache/johnzon>, <https://johnzon.apache.org/>, <https://johnzon.apache.org/scm.html>
