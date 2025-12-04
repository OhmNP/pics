; PhotoSync Server - Inno Setup Installer Script
; This script creates a Windows installer (.exe) for PhotoSync Server
; Requires: Inno Setup 6.0 or higher (https://jrsoftware.org/isinfo.php)

#define MyAppName "PhotoSync Server"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "PhotoSync Team"
#define MyAppURL "https://github.com/yourusername/photosync"
#define MyAppExeName "PhotoSyncServer.exe"

[Setup]
; Application information
AppId={{A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; Installation directories
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes

; Output
OutputDir=installer
OutputBaseFilename=PhotoSync-Server-Setup-{#MyAppVersion}
SetupIconFile=..\assets\icon.ico  ; Optional: add your icon
Compression=lzma
SolidCompression=yes

; Privileges
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog

; UI
WizardStyle=modern
DisableWelcomePage=no

; License and info
LicenseFile=LICENSE.txt  ; Optional: add LICENSE.txt
InfoBeforeFile=README.txt  ; Optional: add README.txt

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "quicklaunchicon"; Description: "{cm:CreateQuickLaunchIcon}"; GroupDescription: "{cm:AdditionalIcons}"; OnlyBelowVersion: 6.1; Check: not IsAdminInstallMode

[Files]
; Main executable
Source: "build\Release\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

; Configuration
Source: "server.conf"; DestDir: "{app}"; Flags: ignoreversion confirmoverwrite

; Test client
Source: "test-client\build\Release\MockClient.exe"; DestDir: "{app}\test-client"; Flags: ignoreversion

; Documentation
Source: "SETUP.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "TESTING.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "BUILD.md"; DestDir: "{app}"; Flags: ignoreversion

; VC++ Redistributable (optional - uncomment if needed)
; Source: "redist\vc_redist.x64.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall

[Icons]
; Start Menu
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Test PhotoSync Server"; Filename: "{app}\test-client\MockClient.exe"; Parameters: "--photos 10"
Name: "{group}\Configuration"; Filename: "notepad.exe"; Parameters: """{app}\server.conf"""
Name: "{group}\Server Logs"; Filename: "notepad.exe"; Parameters: """{app}\server.log"""
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"

; Desktop icon (optional)
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

; Quick Launch (optional, Windows 7 and below)
Name: "{userappdata}\Microsoft\Internet Explorer\Quick Launch\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: quicklaunchicon

[Run]
; Optional: Install VC++ Redistributable
; Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/quiet /norestart"; StatusMsg: "Installing Visual C++ Redistributable..."; Check: VCRedistNeedsInstall

; Optional: Run server after installation
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Delete generated files that weren't part of installation
Type: files; Name: "{app}\photosync.db"
Type: files; Name: "{app}\server.log"
Type: filesandordirs; Name: "{app}\storage"

[Code]
// Check if VC++ Redistributable is needed (optional)
function VCRedistNeedsInstall: Boolean;
var
  Version: String;
begin
  // Check if VC++ 2022 Redistributable is installed
  if RegQueryStringValue(HKEY_LOCAL_MACHINE,
    'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64', 'Version', Version) then
  begin
    Result := False;
  end
  else
  begin
    Result := True;
  end;
end;

// Custom messages
procedure InitializeWizard;
begin
  // You can add custom wizard pages here if needed
end;

[Messages]
; Custom messages
WelcomeLabel2=This will install [name/ver] on your computer.%n%nPhotoSync Server is a photo synchronization server for backing up photos from Android devices.%n%nThe installer will:%n%n• Install the PhotoSync server%n• Create necessary configuration files%n• Set up test utilities%n%nIt is recommended that you close all other applications before continuing.
