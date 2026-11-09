using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;

internal static class SetupBootstrap
{
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBox(IntPtr owner, string text, string caption, uint type);

    [STAThread]
    private static int Main()
    {
        string work = Path.Combine(Path.GetTempPath(), "ZerdeAI-Setup-" + Guid.NewGuid().ToString("N"));
        string stage = "create temporary work folder";
        try
        {
            Directory.CreateDirectory(work);
            stage = "extract setup resources";
            Extract("ZerdeAI_Installer", Path.Combine(work, "Install-ZerdeAI.ps1"));
            Extract("ZerdeAI_Payload", Path.Combine(work, "payload.zip"));
            string ps = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), @"WindowsPowerShell\v1.0\powershell.exe");
            if (!File.Exists(ps)) throw new FileNotFoundException("Windows PowerShell was not found.", ps);
            stage = "launch setup window";
            ProcessStartInfo start = new ProcessStartInfo(ps,
                "-NoLogo -NoProfile -STA -ExecutionPolicy Bypass -File \"" + Path.Combine(work, "Install-ZerdeAI.ps1") + "\"");
            start.WorkingDirectory = work;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;
            using (Process process = Process.Start(start))
            {
                string stdout = process.StandardOutput.ReadToEnd();
                string stderr = process.StandardError.ReadToEnd();
                process.WaitForExit();
                if (process.ExitCode != 0) File.WriteAllText(Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location), "ZerdeAI_Setup.error.log"), "PowerShell exit code: " + process.ExitCode + Environment.NewLine + stdout + Environment.NewLine + stderr);
                return process.ExitCode;
            }
        }
        catch (Exception ex)
        {
            try { File.WriteAllText(Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location), "ZerdeAI_Setup.error.log"), stage + Environment.NewLine + ex); } catch { }
            MessageBox(IntPtr.Zero, "Не удалось открыть установщик ZerdeAI. Этап: " + stage + ". Подробности: ZerdeAI_Setup.error.log", "ZerdeAI", 0x10);
            return 2;
        }
        finally
        {
            try { if (Directory.Exists(work)) Directory.Delete(work, true); } catch { }
        }
    }

    private static void Extract(string resource, string destination)
    {
        using (Stream input = Assembly.GetExecutingAssembly().GetManifestResourceStream(resource))
        {
            if (input == null) throw new InvalidOperationException("В установщике отсутствует компонент " + resource + ".");
            if (resource == "ZerdeAI_Installer")
            {
                using (StreamReader reader = new StreamReader(input, new UTF8Encoding(false), true))
                    File.WriteAllText(destination, reader.ReadToEnd(), new UTF8Encoding(true));
                return;
            }
            using (FileStream output = File.Create(destination)) input.CopyTo(output);
        }
    }
}
