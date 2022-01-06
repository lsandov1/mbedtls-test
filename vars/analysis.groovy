/*
 *  Copyright (c) 2019-2021, Arm Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
 */

import groovy.transform.Field

// A static field has its content preserved across stages.
@Field static outcome_stashes = []

def stash_outcomes(job_name) {
    def stash_name = job_name + '-outcome'
    if (findFiles(glob: '*-outcome.csv')) {
        stash(name: stash_name,
              includes: '*-outcome.csv',
              allowEmpty: true)
        outcome_stashes.add(stash_name)
    }
}

def gather_outcomes() {
    // After running on an old branch which doesn't have the outcome
    // file generation mechanism, or after running a partial run,
    // there may not be any outcome file. In this case, silently
    // do nothing.
    if (outcome_stashes.isEmpty()) {
        return
    }
    println 'helper-container-host'
    node('helper-container-host') {
        dir('outcomes') {
            deleteDir()
            try {
                checkout_repo.checkout_repo()
                dir('csvs') {
                    for (stash_name in outcome_stashes) {
                        unstash(stash_name)
                    }
                    sh 'cat *.csv >../outcomes.csv'
                    deleteDir()
                }
                try {
                    if (fileExists('tests/scripts/analyze_outcomes.py')) {
                        sh 'tests/scripts/analyze_outcomes.py outcomes.csv'
                    }
                } finally {
                    sh 'xz outcomes.csv'
                    archiveArtifacts(artifacts: 'outcomes.csv.xz',
                    fingerprint: true, allowEmptyArchive: true)
                }
            } finally {
                deleteDir()
            }
        }
    }
}

def analyze_results() {
    gather_outcomes()
}

def analyze_results_and_notify_github() {
    try {
        analyze_results()
        common.maybe_notify_github "Result analysis", 'SUCCESS', 'OK'
    } catch (err) {
        common.maybe_notify_github "Result analysis", 'FAILURE',
                                   'Analysis failed'
        throw (err)
    }
}
