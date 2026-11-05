package com.office.application.security;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Checks the PC's protection (Windows 10/11): antivirus, real-time protection, firewall, disk encryption,
 * Windows updates, sign-in password. Read-only: it never changes any setting. Needs no admin rights.
 */
public final class SystemSecurityCheck {
    private SystemSecurityCheck() { }

    public enum Status { OK, WARNING, UNKNOWN }

    public record Item(String name, Status status, String detail, String howToFix) { }

    /** PowerShell that prints simple KEY=VALUE lines; every part is optional and fails quietly. */
    static final String SCRIPT = String.join("\n",
            "$ErrorActionPreference='SilentlyContinue'",
            "$av = Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct",
            "if ($av) { foreach ($a in $av) { 'AV=' + $a.displayName + '|' + $a.productState } } else { 'AV=NONE' }",
            "$mp = Get-MpComputerStatus",
            "if ($mp) { 'DEFENDER_RT=' + $mp.RealTimeProtectionEnabled; 'DEFENDER_SIG_AGE=' + $mp.AntivirusSignatureAge }",
            "foreach ($p in (Get-NetFirewallProfile)) { 'FW=' + $p.Name + '|' + $p.Enabled }",
            "try { 'BITLOCKER=' + (New-Object -ComObject Shell.Application).NameSpace($env:SystemDrive).Self.ExtendedProperty('System.Volume.BitLockerProtection') } catch { 'BITLOCKER=' }",
            "$hf = Get-HotFix | Where-Object { $_.InstalledOn } | Sort-Object InstalledOn -Descending | Select-Object -First 1",
            "if ($hf) { 'LAST_UPDATE=' + $hf.InstalledOn.ToString('yyyy-MM-dd') }",
            "$u = Get-LocalUser -Name $env:USERNAME",
            "if ($u) { 'PW_REQUIRED=' + $u.PasswordRequired }",
            "$os = Get-CimInstance Win32_OperatingSystem",
            "if ($os) { 'OS=' + $os.Caption + ' (build ' + $os.BuildNumber + ')' }");

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Runs the checks (takes a few seconds; call from a background thread). */
    public static List<Item> run() {
        if (!isWindows()) {
            return List.of(new Item("PC security check", Status.UNKNOWN,
                    "Automatic checks run on Windows 10/11 only.",
                    "On Linux: use a login password and full-disk encryption (LUKS), keep the system updated."));
        }
        try {
            String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            p.getOutputStream().close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = p.getInputStream()) { in.transferTo(out); } catch (Exception ignored) { }
            });
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return List.of(new Item("PC security check", Status.UNKNOWN, "The check took too long and was stopped.", "Try again from Admin > PC security check."));
            }
            reader.join(2000);
            return parse(out.toString(StandardCharsets.UTF_8), LocalDate.now());
        } catch (Exception e) {
            return List.of(new Item("PC security check", Status.UNKNOWN, "Could not run the check: " + e.getMessage(), "Try again later."));
        }
    }

    /** Turns the script output into readable results (public for tests). */
    public static List<Item> parse(String output, LocalDate today) {
        List<String> av = new ArrayList<>(), fw = new ArrayList<>();
        String defenderRt = null, sigAge = null, bitlocker = null, lastUpdate = null, pwRequired = null, os = null;
        for (String raw : output.split("\\R")) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            if (eq < 1) continue;
            String k = line.substring(0, eq), v = line.substring(eq + 1).trim();
            switch (k) {
                case "AV" -> av.add(v);
                case "FW" -> fw.add(v);
                case "DEFENDER_RT" -> defenderRt = v;
                case "DEFENDER_SIG_AGE" -> sigAge = v;
                case "BITLOCKER" -> bitlocker = v;
                case "LAST_UPDATE" -> lastUpdate = v;
                case "PW_REQUIRED" -> pwRequired = v;
                case "OS" -> os = v;
                default -> { }
            }
        }
        List<Item> items = new ArrayList<>();

        // 1. Antivirus (any product registered with Windows Security Center)
        List<String> active = new ArrayList<>(), off = new ArrayList<>(), outdated = new ArrayList<>();
        for (String a : av) {
            if (a.equals("NONE")) continue;
            String[] parts = a.split("\\|");
            String name = parts[0];
            Integer state = parts.length > 1 ? parseInt(parts[1]) : null;
            if (state == null) { active.add(name); continue; }
            int enabledByte = (state >> 8) & 0xFF;     // 0x10/0x11 = on
            int defsByte = state & 0xFF;               // 0x00 = definitions up to date
            if (enabledByte == 0x10 || enabledByte == 0x11) {
                active.add(name);
                if (defsByte != 0x00) outdated.add(name);
            } else off.add(name);
        }
        if (!active.isEmpty())
            items.add(new Item("Antivirus", outdated.isEmpty() ? Status.OK : Status.WARNING,
                    "Active: " + String.join(", ", active) + (outdated.isEmpty() ? "" : " (virus definitions out of date)"),
                    outdated.isEmpty() ? "" : "Open the antivirus and update it (Windows Security > Virus & threat protection > Check for updates)."));
        else if (!off.isEmpty())
            items.add(new Item("Antivirus", Status.WARNING, "Installed but turned OFF: " + String.join(", ", off),
                    "Windows Security > Virus & threat protection > turn on Real-time protection."));
        else
            items.add(new Item("Antivirus", av.isEmpty() ? Status.UNKNOWN : Status.WARNING,
                    av.isEmpty() ? "Could not read antivirus status." : "No antivirus found.",
                    "Windows Security > Virus & threat protection: make sure protection is on."));

        // 2. Defender real-time protection (only when Defender is in use)
        if (defenderRt != null && active.stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains("defender"))) {
            boolean on = isTrue(defenderRt);
            Integer age = parseInt(sigAge);
            items.add(new Item("Real-time protection", on ? (age != null && age > 7 ? Status.WARNING : Status.OK) : Status.WARNING,
                    on ? "On" + (age != null ? ", definitions " + age + " day(s) old" : "") : "OFF",
                    on && (age == null || age <= 7) ? "" : "Windows Security > Virus & threat protection: turn on Real-time protection and check for updates."));
        }

        // 3. Firewall: every profile must be on
        if (fw.isEmpty()) {
            items.add(new Item("Firewall", Status.UNKNOWN, "Could not read firewall status.", "Windows Security > Firewall & network protection."));
        } else {
            List<String> offProfiles = fw.stream().filter(f -> !isTrue(f.substring(f.indexOf('|') + 1)))
                    .map(f -> f.substring(0, f.indexOf('|') < 0 ? f.length() : f.indexOf('|'))).toList();
            items.add(new Item("Firewall", offProfiles.isEmpty() ? Status.OK : Status.WARNING,
                    offProfiles.isEmpty() ? "On for all networks" : "OFF for: " + String.join(", ", offProfiles),
                    offProfiles.isEmpty() ? "" : "Windows Security > Firewall & network protection: turn on for every network."));
        }

        // 4. Disk encryption (BitLocker / Device encryption) on the system drive
        Integer bl = parseInt(bitlocker);
        if (bl == null || bl == 0)
            items.add(new Item("Disk encryption", Status.UNKNOWN, "Could not read encryption status.",
                    "Settings > Privacy & security > Device encryption (or Control Panel > BitLocker): turn it on."));
        else if (bl == 1 || bl == 3)
            items.add(new Item("Disk encryption", Status.OK, bl == 1 ? "BitLocker is on" : "Encryption in progress", ""));
        else
            items.add(new Item("Disk encryption", Status.WARNING,
                    bl == 5 ? "BitLocker is suspended" : "The system drive is NOT encrypted: if the PC is stolen, the books can be read.",
                    "Settings > Privacy & security > Device encryption, or Control Panel > BitLocker Drive Encryption > Turn on."));

        // 5. Windows updates
        LocalDate last = null;
        try { if (lastUpdate != null && !lastUpdate.isBlank()) last = LocalDate.parse(lastUpdate); } catch (Exception ignored) { }
        if (last == null)
            items.add(new Item("Windows updates", Status.UNKNOWN, "Could not read the last update date.", "Settings > Windows Update > Check for updates."));
        else {
            long days = java.time.temporal.ChronoUnit.DAYS.between(last, today);
            items.add(new Item("Windows updates", days > 60 ? Status.WARNING : Status.OK,
                    "Last update installed " + last + " (" + days + " days ago)",
                    days > 60 ? "Settings > Windows Update > Check for updates, and install them." : ""));
        }

        // 6. Sign-in password
        if (pwRequired == null || pwRequired.isBlank())
            items.add(new Item("Windows sign-in password", Status.UNKNOWN, "Could not check (Microsoft or work account).",
                    "Make sure this Windows account has a password or PIN: Settings > Accounts > Sign-in options."));
        else
            items.add(new Item("Windows sign-in password", isTrue(pwRequired) ? Status.OK : Status.WARNING,
                    isTrue(pwRequired) ? "Required" : "This Windows account may not need a password",
                    isTrue(pwRequired) ? "" : "Settings > Accounts > Sign-in options: add a password or PIN."));

        if (os != null) items.add(new Item("Windows version", Status.OK, os, ""));
        return items;
    }

    public static long warnings(List<Item> items) {
        return items.stream().filter(i -> i.status() == Status.WARNING).count();
    }

    private static boolean isTrue(String v) {
        return v != null && (v.trim().equalsIgnoreCase("true") || v.trim().equals("1"));
    }

    private static Integer parseInt(String v) {
        try { return v == null || v.isBlank() ? null : Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }
}
