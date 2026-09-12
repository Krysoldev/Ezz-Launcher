#define MyAppName "Ezz Launcher"
#define MyAppVersion "1.0.1"
#define MyAppPublisher "Ezz"
#define MyAppExeName "EzzLauncher.exe"
#define MyAppId "{{18E2E7AA-6B88-3C5F-86FF-FF4314365EC5}"

[Setup]
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\EzzLauncher
UsePreviousAppDir=yes
DirExistsWarning=no
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\..\release
OutputBaseFilename=EzzLauncher-Setup-1.0.1
SetupIconFile=..\..\app\desktop\src\jvmMain\resources\icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
CloseApplications=no
RestartApplications=no
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; Clean up old version JAR files so no obsolete version files remain
Type: files; Name: "{app}\app\*.jar"

[Files]
Source: "..\..\app\desktop\build\dist\main-release\app\EzzLauncher\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Check: ShouldCreateDesktopIcon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
var
  bHadDesktopIcon: Boolean;

function CheckHadDesktopIcon(): Boolean;
begin
  Result := FileExists(ExpandConstant('{userdesktop}\{#MyAppName}.lnk')) or
            FileExists(ExpandConstant('{userdesktop}\EzzLauncher.lnk')) or
            FileExists(ExpandConstant('{commondesktop}\{#MyAppName}.lnk')) or
            FileExists(ExpandConstant('{commondesktop}\EzzLauncher.lnk'));
end;

function ShouldCreateDesktopIcon(): Boolean;
begin
  Result := bHadDesktopIcon or WizardIsTaskSelected('desktopicon');
end;

function IsAppInstalled(): Boolean;
begin
  Result := RegKeyExists(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1') or
            RegKeyExists(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{48365AB6-354E-3346-9228-A690CF7DD14C}') or
            FileExists(ExpandConstant('{localappdata}\EzzLauncher\{#MyAppExeName}'));
end;

function IsLauncherRunning(): Boolean;
var
  ResultCode: Integer;
begin
  // Check if EzzLauncher.exe specifically is in the running task list
  Result := Exec('cmd.exe', '/c tasklist /nh /fi "IMAGENAME eq EzzLauncher.exe" | findstr /i "EzzLauncher.exe"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
  Attempts: Integer;
begin
  Result := '';
  if IsLauncherRunning() then
  begin
    if not WizardSilent() then
    begin
      if MsgBox('Ezz Launcher is currently running.' + #13#10#13#10 +
                'Setup needs to close the launcher to update application files.' + #13#10 +
                'Minecraft (if running) will NOT be closed or affected.' + #13#10#13#10 +
                'Do you want Setup to close Ezz Launcher now and proceed with the update?',
                mbConfirmation, MB_YESNO) <> IDYES then
      begin
        Result := 'Update cancelled: Ezz Launcher is currently running.';
        Exit;
      end;
    end;

    // Send graceful close specifically to EzzLauncher.exe
    Exec('taskkill.exe', '/im EzzLauncher.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Attempts := 0;
    while IsLauncherRunning() and (Attempts < 6) do
    begin
      Sleep(500);
      Attempts := Attempts + 1;
    end;

    // If still running after waiting, ensure EzzLauncher.exe exits cleanly so files can be replaced
    if IsLauncherRunning() then
    begin
      Exec('taskkill.exe', '/f /im EzzLauncher.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
      Sleep(1000);
    end;

    if IsLauncherRunning() then
    begin
      Result := 'Setup could not close Ezz Launcher. Please close it manually and retry the update.';
      Exit;
    end;
  end;
end;

procedure InitializeWizard;
begin
  bHadDesktopIcon := CheckHadDesktopIcon();
  if bHadDesktopIcon then
    WizardSelectTasks('desktopicon');

  if (not WizardSilent()) and IsAppInstalled() then
  begin
    WizardForm.Caption := 'Update - ' + ExpandConstant('{#MyAppName}');
    WizardForm.WelcomeLabel1.Caption := 'Update ' + ExpandConstant('{#MyAppName}');
    WizardForm.WelcomeLabel2.Caption := 'An existing installation of ' + ExpandConstant('{#MyAppName}') + ' was detected on your computer.' + #13#10#13#10 +
      'Setup will update the application to version {#MyAppVersion}.' + #13#10#13#10 +
      'All your accounts, instances, worlds, mods, and launcher settings will be preserved.' + #13#10#13#10 +
      'Click Next to continue with the update.';
    WizardForm.FinishedHeadingLabel.Caption := ExpandConstant('{#MyAppName}') + ' Updated Successfully';
    WizardForm.FinishedLabel.Caption := ExpandConstant('{#MyAppName}') + ' has been updated to version {#MyAppVersion}.' + #13#10#13#10 +
      'Click Finish to exit Setup and launch the updated application.';
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (not WizardSilent()) and IsAppInstalled() and (CurPageID = wpReady) then
  begin
    WizardForm.NextButton.Caption := '&Update';
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
  begin
    // Clean legacy MSI product registration so only ONE Ezz Launcher appears in Windows Settings -> Apps
    RegDeleteKeyIncludingSubkeys(HKEY_CURRENT_USER, 'Software\Microsoft\Installer\Products\6BA56384E453643329826A09FCD71DC4');
    RegDeleteKeyIncludingSubkeys(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{48365AB6-354E-3346-9228-A690CF7DD14C}');
    RegDeleteKeyIncludingSubkeys(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{CFA5A7AF-EB90-46D9-965A-1FA302FF5AEC}');
    
    // Clean legacy shortcuts from previous runs and other installers to prevent duplicates
    DeleteFile(ExpandConstant('{userprograms}\Ezz Launcher\EzzLauncher.lnk'));
    RemoveDir(ExpandConstant('{userprograms}\Ezz Launcher'));
    DeleteFile(ExpandConstant('{commonprograms}\Ezz Launcher\EzzLauncher.lnk'));
    RemoveDir(ExpandConstant('{commonprograms}\Ezz Launcher'));
    DeleteFile(ExpandConstant('{userprograms}\Unknown\EzzLauncher.lnk'));
    DeleteFile(ExpandConstant('{userprograms}\EzzLauncher.lnk'));
    DeleteFile(ExpandConstant('{commonprograms}\EzzLauncher.lnk'));

    // Clean duplicate desktop shortcuts
    DeleteFile(ExpandConstant('{userdesktop}\EzzLauncher.lnk'));
    DeleteFile(ExpandConstant('{commondesktop}\EzzLauncher.lnk'));
  end;
end;
