<!--

    Copyright (c) 2026 Contributors to the Eclipse Foundation

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License v. 2.0, which is available at
    http://www.eclipse.org/legal/epl-2.0.

    This Source Code may also be made available under the following Secondary
    Licenses when the conditions for such availability set forth in the
    Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
    version 2 with the GNU Classpath Exception, which is available at
    https://www.gnu.org/software/classpath/license.html.

    SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

-->

# JSONPath Compliance Test Suite (third-party content)

`cts.json`, `LICENSE` and `NOTICE` in this directory are copied unchanged from the
[JSONPath Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite)
at commit `20ebdcce5008c8a777ee9c80ad853ecf606950b5` (2026-10-13). `cts.json` has the SHA-256
checksum `f4ab5e3d3c17b40bf59cb0fa94425790b1401eb30c35a54902a119c65c851d36`.

They are licensed under the BSD-2-Clause license, see `LICENSE` and `NOTICE`, not under the
licenses of this project.

`ee.jakarta.tck.jsonp.api.jsonpathtests.JsonPathComplianceTests` runs every test case of
`cts.json` as one test. To update the suite, replace the three files with those of a newer commit
and update the commit and checksum above.
