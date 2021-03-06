// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.deploymentmanager.autogen;

import com.google.cloud.deploymentmanager.autogen.Autogen.SharedSupportFilesStrategy;
import com.google.cloud.deploymentmanager.autogen.proto.DeploymentPackageInput;
import com.google.cloud.deploymentmanager.autogen.proto.SolutionPackage;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({VerifySolutionFileSetTest.class, VerifyAutogenFileContent.class})
public class AutogenMediumTestsSuite {
  static final String RELATIVE_TESTDATA_PATH =
      "javatests/com/google/cloud/deploymentmanager/autogen/testdata/";

  static final String TESTDATA_PATH =
      System.getenv("JAVA_RUNFILES")
          + "/__main__/"
          + RELATIVE_TESTDATA_PATH;

  static class Solution {
    final String name;
    final File solutionFolder;
    final File goldenFolder;
    final Supplier<SolutionPackage> solutionPackage;

    static Collection<String[]> allSolutionsActualFilesDesc() {
      List<String[]> files = new ArrayList();
      for (Solution solution : findAllSolutions()) {
        for (SolutionPackage.File f : solution.solutionPackage.get().getFilesList()) {
          files.add(
              new String[] {
                solution.name, solution.goldenFolder.getPath(), f.getPath(), f.getContent()
              });
        }
      }
      return files;
    }

    /** Finds all solutions under testdata folder. */
    static Collection<Solution> findAllSolutions() {
      File root = new File(TESTDATA_PATH);
      return FluentIterable.from(Files.fileTraverser().depthFirstPreOrder(root))
          .filter(Files.isFile())
          .filter(
              new Predicate<File>() {
                @Override
                public boolean apply(File f) {
                  return "input.prototext".equals(f.getName());
                }
              })
          .transform(
              new Function<File, File>() {
                @Override
                public File apply(File f) {
                  return f.getParentFile();
                }
              })
          .transform(relativePathFunction(root))
          .transform(
              new Function<String, Solution>() {
                @Override
                public Solution apply(String name) {
                  return new Solution(name);
                }
              })
          .toList();
    }

    Solution(String folder) {
      name = folder;
      solutionFolder = new File(TESTDATA_PATH + folder);
      goldenFolder = new File(solutionFolder, "golden");
      solutionPackage =
          Suppliers.memoize(
              new Supplier<SolutionPackage>() {
                @Override
                public SolutionPackage get() {
                  try {
                    DeploymentPackageInput.Builder input = DeploymentPackageInput.newBuilder();
                    TextFormat.getParser().merge(Files.asCharSource(
                        new File(solutionFolder, "input.prototext"), StandardCharsets.UTF_8)
                            .read(), input);
                    return Autogen.getInstance().generateDeploymentPackage(input.build(),
                        SharedSupportFilesStrategy.INCLUDED);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
              });
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  static Function<File, String> relativePathFunction(final File parent) {
    return new Function<File, String>() {
      @Override
      public String apply(File f) {
        Path parentPath = Paths.get(parent.getAbsolutePath()).normalize();
        Path filePath = Paths.get(f.getAbsolutePath()).normalize();
        return parentPath.relativize(filePath).toString();
      }
    };
  }
}
