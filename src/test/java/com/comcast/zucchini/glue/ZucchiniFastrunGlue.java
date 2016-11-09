/**
 * Copyright 2014 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.comcast.zucchini.glue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.testng.Assert;

import com.comcast.zucchini.TestContext;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

public class ZucchiniFastrunGlue {
    static private class RanScenarios {
        final public Integer scenarioId;
        final public TestContext runBy;
        public RanScenarios(int si, TestContext tc) {
            this.scenarioId = new Integer(si);
            this.runBy = tc;
        }
    }

    static private List<RanScenarios> runScenarios = new ArrayList<>();

    @Given("The scenario (\\d+) is saved to global list")
    public void scenarioMarkedAsRun(int scenarioId) {
        RanScenarios rs = new RanScenarios(scenarioId, TestContext.getCurrent());
        synchronized (runScenarios) {
            runScenarios.add(rs);
        }
    }

    @Then("Verify no scenario duplicates")
    public void verifyNoDuplicateScenarios() {
        Map<Integer, TestContext> sort = new TreeMap<>();
        synchronized (runScenarios) {
            for(RanScenarios rs : runScenarios) {
                String msg = "LOGICAL ERROR, fix ZucchiniFastrunGlue.java";
                if (sort.containsKey(rs.scenarioId)) {
                    msg = "Scenario ["+rs.scenarioId+"] run on context ["+rs.runBy.name()+"] and ["+sort.get(rs.scenarioId).name()+"]";
                }
                Assert.assertFalse(sort.containsKey(rs.scenarioId), msg);
                sort.put(rs.scenarioId, rs.runBy);
            }
            Assert.assertEquals(sort.size(), runScenarios.size(), "We have a scenario being run by more than one test context");
        }
    }

}
