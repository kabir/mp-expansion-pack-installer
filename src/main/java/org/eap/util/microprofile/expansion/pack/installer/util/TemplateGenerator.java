package org.eap.util.microprofile.expansion.pack.installer.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jboss.as.patching.generator.PatchGenLogger;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TemplateGenerator {
    private static final String CREATE_TEMPLATE = "--create-template";
    private static final String H = "-h";
    private static final String HELP = "--help";

    private final String patchID;
    private final String eapCpVersion;
    private final String mpXpVersion;

    public TemplateGenerator(String patchID, String eapCpVersion, String mpXpVersion) {
        this.patchID = patchID;
        this.eapCpVersion = eapCpVersion;
        this.mpXpVersion = mpXpVersion;
    }

    static void generate(final String... args) throws Exception{

        String patchID = UUID.randomUUID().toString();
        String eapCpVersion = null;
        String mpXpVersion = null;
        final int argsLength = args.length;
        for (int i = 0; i < argsLength; i++) {
            final String arg = args[i];
            try {
                if (HELP.equals(arg) || H.equalsIgnoreCase(arg)) {
                    usage();
                    return;
                } else if (arg.equals(CREATE_TEMPLATE)) {
                    patchID = args[++i];
                    eapCpVersion = args[++i];
                    mpXpVersion = args[++i];
                    continue;
                } else {
                    System.err.println(PatchGenLogger.argumentExpected(arg));
                    usage();
                    return;
                }
            } catch (IndexOutOfBoundsException e) {
                System.err.println(PatchGenLogger.argumentExpected(arg));
                usage();
                return;
            }
        }

        TemplateGenerator templateGenerator = new TemplateGenerator(patchID, eapCpVersion, mpXpVersion);
        templateGenerator.createPatchConfigXml();
    }

    static void usage() {
        System.err.println("USAGE:");
        System.err.println("patch-gen.sh --create-template <patch-id> <eap-cp-version> <microprofile-expansion-pack-version>");
        System.err.println();
        System.err.println("this will create a patch-config-[patch-id].xml adjusted for the EAP CP and MP Expansion Pack versions");
    }

    private void createPatchConfigXml() throws Exception {
        String xml = readBundledPatchConfigXml();
        Files.write(Paths.get("patch-config-" + patchID + ".xml"), xml.getBytes(StandardCharsets.UTF_8));
    }

    private String readBundledPatchConfigXml() throws Exception {
        URL url = TemplateGenerator.class.getProtectionDomain().getCodeSource().getLocation();
        if (!url.toString().contains(".jar")) {
            throw new IllegalStateException("The Template Creator must be run from the distributed jar. It should not be unzipped!");
        }

        File file = new File(url.toURI());
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null)
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (entry.getName().equals("patch-config.xml")) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zin))) {
                            StringBuffer sb = new StringBuffer();
                            String line = reader.readLine();
                            while (line != null) {
                                line = line.replace("${expansion.pack.version}", mpXpVersion);
                                line = line.replace("${eap.cp.version}", eapCpVersion);
                                sb.append(line);
                                line = reader.readLine();
                            }
                            return sb.toString();
                        }
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
            }
        throw new IllegalStateException("Could not find patch-config.xml in  " + Paths.get(url.toURI()));
    }
}