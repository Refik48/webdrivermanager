/*
 * (C) Copyright 2020 Boni Garcia (http://bonigarcia.github.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License..
 *
 */
package io.github.bonigarcia.wdm.online;

import static io.github.bonigarcia.wdm.etc.Config.isNullOrEmpty;
import static java.io.File.separator;
import static java.lang.Integer.signum;
import static java.lang.Integer.valueOf;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Arrays.copyOf;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;

import io.github.bonigarcia.wdm.etc.Architecture;
import io.github.bonigarcia.wdm.etc.OperatingSystem;
import io.github.bonigarcia.wdm.etc.WebDriverManagerException;

/**
 * Handler for URLs.
 *
 * @author Boni Garcia (boni.gg@gmail.com)
 * @since 4.0.0
 */
public class UrlHandler {

    public static final String ALPHA = "alpha";
    public static final String BETA = "beta";

    final Logger log = getLogger(lookup().lookupClass());

    List<URL> candidateUrls;
    String driverVersion;
    String shortDriverName;
    boolean isUseBeta;

    public UrlHandler(List<URL> candidateUrls, String driverVersion,
            String shortDriverName, boolean isUseBeta) {
        this.candidateUrls = candidateUrls;
        this.driverVersion = driverVersion;
        this.shortDriverName = shortDriverName;
        this.isUseBeta = isUseBeta;
    }

    public void filterByDriverName(String driverName) {
        candidateUrls = candidateUrls.stream()
                .filter(url -> url.getFile().contains(driverName)
                        && !url.getFile().contains("-symbols"))
                .collect(toList());
    }

    public void filterByVersion(String driverVersion) {
        this.driverVersion = driverVersion;
        candidateUrls = candidateUrls.stream()
                .filter(url -> url.getFile().contains(driverVersion))
                .collect(toList());
    }

    public void filterByLatestVersion(Function<URL, String> getCurrentVersion) {
        log.trace("Checking the lastest version using URL list {}",
                candidateUrls);
        List<URL> out = new ArrayList<>();
        List<URL> copyOfList = new ArrayList<>(candidateUrls);
        String foundFriverVersion = null;

        for (URL url : copyOfList) {
            try {
                if (isNotStable(url)) {
                    continue;
                }
                String currentVersion = getCurrentVersion.apply(url);
                if (isNullOrEmpty(foundFriverVersion)) {
                    foundFriverVersion = currentVersion;
                }
                if (versionCompare(currentVersion, foundFriverVersion) > 0) {
                    foundFriverVersion = currentVersion;
                    out.clear();
                }
                if (url.getFile().contains(foundFriverVersion)) {
                    out.add(url);
                }
            } catch (Exception e) {
                log.trace("There was a problem with URL {} : {}", url,
                        e.getMessage());
                candidateUrls.remove(url);
            }
        }
        this.driverVersion = foundFriverVersion;
        this.candidateUrls = out;
    }

    public void filterByBeta(boolean useBeta) {
        if (!useBeta) {
            log.trace("URLs before filtering by beta versions: {}",
                    candidateUrls);
            candidateUrls = candidateUrls.stream()
                    .filter(url -> !url.getFile().toLowerCase().contains(BETA)
                            && !url.getFile().toLowerCase().contains(ALPHA))
                    .collect(toList());
            log.trace("URLs after filtering by beta versions: {}",
                    candidateUrls);
        }
    }

    public void filterByOs(String driverName, String osName) {
        if (!driverName.equalsIgnoreCase("IEDriverServer")
                && !driverName.equalsIgnoreCase("selenium-server-standalone")) {
            log.trace("URLs before filtering by OS ({}): {}", osName,
                    candidateUrls);
            List<URL> out = new ArrayList<>();

            for (URL url : candidateUrls) {
                for (OperatingSystem os : OperatingSystem.values()) {
                    if (((osName.contains(os.name())
                            && url.getFile().toUpperCase().contains(os.name()))
                            || (osName.equalsIgnoreCase("mac") && url.getFile()
                                    .toLowerCase().contains("osx")))
                            && !out.contains(url)) {
                        out.add(url);
                    }
                }
            }

            log.trace("URLs after filtering by OS ({}): {}", osName, out);
            candidateUrls = out;
        }
    }

    public void filterByArch(Architecture arch, boolean forcedArch) {
        log.trace("URLs before filtering by architecture ({}): {}", arch,
                candidateUrls);
        List<URL> out = new ArrayList<>(candidateUrls);

        if ((forcedArch || out.size() > 1) && arch != null) {
            for (URL url : candidateUrls) {
                if (!url.getFile().contains("x86")
                        && !url.getFile().contains("64")
                        && !url.getFile().contains("i686")
                        && !url.getFile().contains("32")) {
                    continue;
                }
                if (!arch.matchUrl(url)) {
                    out.remove(url);
                }
            }
        }

        log.trace("URLs after filtering by architecture ({}): {}", arch, out);

        if (out.isEmpty() && !forcedArch && !candidateUrls.isEmpty()) {
            out = singletonList(candidateUrls.get(candidateUrls.size() - 1));
            log.trace(
                    "Empty URL list after filtering by architecture ... using last candidate: {}",
                    out);
        }
        candidateUrls = out;
    }

    public void filterByDistro(String os, String driverName)
            throws IOException {
        String distro = getDistroName();
        if (os.equalsIgnoreCase("linux") && driverName.contains("phantomjs")) {
            String versionToFilter = "2.5.0";
            log.trace("URLs before filtering by Linux distribution ({}): {}",
                    distro, candidateUrls);
            candidateUrls = candidateUrls.stream()
                    .filter(url -> !url.getFile().contains(versionToFilter)
                            || url.getFile().contains(distro))
                    .collect(toList());
            log.trace("URLs after filtering by Linux distribution ({}): {}",
                    distro, candidateUrls);
        }
    }

    public void filterByIgnoredVersions(String... ignoredVersions) {
        if (ignoredVersions != null && ignoredVersions.length > 0
                && !candidateUrls.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("URLs before filtering by ignored versions ({}): {}",
                        Arrays.toString(ignoredVersions), candidateUrls);
            }
            candidateUrls = candidateUrls.stream().filter(url -> Arrays
                    .asList(ignoredVersions).contains(url.getFile()))
                    .collect(toList());
            if (log.isTraceEnabled()) {
                log.trace("URLs after filtering by ignored versions ({}): {}",
                        Arrays.toString(ignoredVersions), candidateUrls);
            }
        }
    }

    public String getDistroName() throws IOException {
        String out = "";
        final String key = "UBUNTU_CODENAME";
        File dir = new File(separator + "etc");
        File[] fileList = new File[0];
        if (dir.exists()) {
            fileList = dir.listFiles(
                    (path, filename) -> filename.endsWith("-release"));
        }
        File fileVersion = new File(separator + "proc", "version");
        if (fileVersion.exists()) {
            fileList = copyOf(fileList, fileList.length + 1);
            fileList[fileList.length - 1] = fileVersion;
        }
        for (File f : fileList) {
            if (f.isDirectory()) {
                continue;
            }
            try (BufferedReader myReader = new BufferedReader(
                    new FileReader(f))) {
                String strLine = null;
                while ((strLine = myReader.readLine()) != null) {
                    if (strLine.contains(key)) {
                        int beginIndex = key.length();
                        out = strLine.substring(beginIndex + 1);
                    }
                }
            }
        }
        return out;
    }

    public void resetList(List<URL> newCandidateUrls) {
        candidateUrls = candidateUrls.stream()
                .filter(url -> !url.getFile().contains(driverVersion))
                .collect(toList());
    }

    public boolean isNotStable(URL url) {
        return !isUseBeta && (url.getFile().toLowerCase().contains(BETA)
                || url.getFile().toLowerCase().contains(ALPHA));
    }

    public Integer versionCompare(String str1, String str2) {
        String[] vals1 = str1.replace("v", "").replace("-" + BETA, "")
                .split("\\.");
        String[] vals2 = str2.replace("v", "").replace("-" + BETA, "")
                .split("\\.");

        if (vals1[0].equals("")) {
            vals1[0] = "0";
        }
        if (vals2[0].equals("")) {
            vals2[0] = "0";
        }

        int i = 0;
        while (i < vals1.length && i < vals2.length
                && vals1[i].equals(vals2[i])) {
            i++;
        }

        if (i < vals1.length && i < vals2.length) {
            return signum(valueOf(vals1[i]).compareTo(valueOf(vals2[i])));
        } else {
            return signum(vals1.length - vals2.length);
        }
    }

    public List<URL> getCandidateUrls() {
        return candidateUrls;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public boolean hasNoCandidateUrl() {
        return candidateUrls.isEmpty();
    }

    public URL getCandidateUrl() {
        if (hasNoCandidateUrl()) {
            String driverVersionLabel = isNullOrEmpty(driverVersion) ? ""
                    : " " + driverVersion;
            String errorMessage = String.format(
                    "No proper candidate URL to download %s%s", shortDriverName,
                    driverVersionLabel);
            throw new WebDriverManagerException(errorMessage);
        }
        return candidateUrls.iterator().next();
    }

}