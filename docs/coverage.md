# Android OSS coverage inputs

`./gradlew unitTestCoverage` is the single reproducible unit-coverage command.
It generates every expected XML report, then `verifyUnitCoverageReports` rejects
any missing or empty report and writes the deterministic aggregate to
`build/reports/coverage/unit-test-summary.txt`. CI uploads exactly these inputs:

| Module | Variant |
| --- | --- |
| `core:auth` | `localDebug` |
| `core:data-api` | `debug` |
| `core:data-firebase` | `debug` |
| `core:datastore` | `debug` |
| `core:domain` | `debug` |
| `core:location` | `debug` |
| `core:model` | `debug` |
| `core:ui` | `debug` |
| `feature:map` | `debug` |

The aggregate consumes each module report once. `core:auth` deliberately uses
only `localDebug`: its local and production implementations are distinct, so
including both would double-count the shared auth sources. The `app` product
variants are likewise not added until they have executable unit tests.

`core:data` and `app` currently have no unit-test sources, so they cannot yet
produce an executable coverage report. They remain visible here rather than
silently shrinking the reporting scope; adding tests to either module requires
adding its report to the guarded list and to the Codecov upload list together.

## Android-backed datastore coverage

`AndroidKeystoreEmailCipher` uses the Android Keystore and therefore cannot be
executed by the JVM unit-test runner. With an authorized connected device or
emulator, run:

```sh
./gradlew :core:datastore:collectDebugCoverage
```

This runs the datastore debug instrumentation and unit tests, then writes the
combined native AGP report to
`core/datastore/build/reports/code_coverage_html_report/global/index.html`.
The device execution data is retained at
`core/datastore/build/outputs/code_coverage/debugAndroidTest/connected/<device>/coverage.ec`,
and the instrumentation result XML is retained beneath
`core/datastore/build/outputs/androidTest-results/connected/debug/`.

The debug test APK uses multidex because its Android-test dependency graph
exceeds the single-dex method limit when coverage instrumentation is enabled.
This device report is evidence for Android-runtime adapters; it is deliberately
separate from the reproducible JVM-only `unitTestCoverage` aggregate and its
Codecov inputs.
