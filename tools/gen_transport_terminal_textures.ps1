# Texture generator 16x16: Transport Registration Terminal.
# Style: old monoblock computer, black case + brass trim, green phosphor screen.
Add-Type -AssemblyName System.Drawing

$outDir = 'F:\recreate\CreateCityExpansionCore\src\main\resources\assets\createcityexpansioncore\textures\block'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$script:caseLo    = [System.Drawing.Color]::FromArgb(255,20,20,20)
$script:caseMid   = [System.Drawing.Color]::FromArgb(255,27,27,27)
$script:caseHi    = [System.Drawing.Color]::FromArgb(255,34,34,34)
$script:brassLo   = [System.Drawing.Color]::FromArgb(255,107,74,24)
$script:brassBase = [System.Drawing.Color]::FromArgb(255,168,118,42)
$script:brassHi   = [System.Drawing.Color]::FromArgb(255,217,168,65)
$script:screenBg  = [System.Drawing.Color]::FromArgb(255,10,43,28)
$script:screenLn  = [System.Drawing.Color]::FromArgb(255,17,56,35)
$script:screenTx  = [System.Drawing.Color]::FromArgb(255,127,224,132)
$script:keyCap    = [System.Drawing.Color]::FromArgb(255,62,62,62)
$script:keyCapLo  = [System.Drawing.Color]::FromArgb(255,46,46,46)
$script:gapDark   = [System.Drawing.Color]::FromArgb(255,16,16,16)

function CaseNoise([int]$x, [int]$y) {
    switch ((($x * 7 + $y * 13 + [int]([math]::Abs($x - $y))) % 4)) {
        0 { $script:caseLo }
        1 { $script:caseMid }
        2 { $script:caseMid }
        3 { $script:caseHi }
    }
}

function NewBitmap { return (New-Object System.Drawing.Bitmap 16, 16) }

function FillCase($bmp) {
    for ($x = 0; $x -lt 16; $x++) {
        for ($y = 0; $y -lt 16; $y++) { [void]$bmp.SetPixel($x, $y, (CaseNoise $x $y)) }
    }
}

function FillRect($bmp, [int]$x0, [int]$x1, [int]$y0, [int]$y1, $color) {
    for ($x = $x0; $x -le $x1; $x++) {
        for ($y = $y0; $y -le $y1; $y++) { [void]$bmp.SetPixel($x, $y, $color) }
    }
}

function SaveBitmap($bmp, [string]$name) {
    $path = Join-Path $outDir $name
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "written: $path"
}

# ---------- 1. FRONT: brass-framed screen + brass buttons ----------
$g = NewBitmap
FillCase $g
FillRect $g 0 15 0 0 $brassLo
FillRect $g 0 15 15 15 $brassLo
FillRect $g 0 0 0 15 $brassLo
FillRect $g 15 15 0 15 $brassLo
FillRect $g 0 15 1 1 $brassBase
FillRect $g 0 15 14 14 $brassBase
FillRect $g 1 1 0 15 $brassBase
FillRect $g 14 14 0 15 $brassBase
[void]$g.SetPixel(0,0,$brassHi); [void]$g.SetPixel(15,0,$brassHi)
[void]$g.SetPixel(0,15,$brassHi); [void]$g.SetPixel(15,15,$brassHi)
FillRect $g 3 12 3 10 $screenBg
FillRect $g 3 12 4 4 $screenLn
FillRect $g 3 12 6 6 $screenLn
FillRect $g 3 12 8 8 $screenLn
FillRect $g 3 12 10 10 $screenLn
# prompt ">" and cursor in phosphor green
[void]$g.SetPixel(4,4,$screenTx); [void]$g.SetPixel(4,5,$screenTx); [void]$g.SetPixel(4,6,$screenTx)
[void]$g.SetPixel(5,5,$screenTx); [void]$g.SetPixel(5,6,$screenTx)
[void]$g.SetPixel(7,6,$screenTx)
[void]$g.SetPixel(4,8,$screenTx); [void]$g.SetPixel(5,8,$screenTx); [void]$g.SetPixel(6,8,$screenTx)
# brass button row below the screen
FillRect $g 3 5 12 12 $brassHi;  FillRect $g 3 5 13 13 $brassBase; FillRect $g 3 5 14 14 $brassLo
FillRect $g 7 9 12 12 $brassHi;  FillRect $g 7 9 13 13 $brassBase; FillRect $g 7 9 14 14 $brassLo
FillRect $g 11 12 12 12 $brassHi; FillRect $g 11 12 13 13 $brassBase; FillRect $g 11 12 14 14 $brassLo
SaveBitmap $g 'transport_registration_terminal_front.png'

# ---------- 2. SIDE: black case with brass side rails ----------
$g = NewBitmap
FillCase $g
FillRect $g 0 0 0 15 $brassLo
FillRect $g 15 15 0 15 $brassLo
FillRect $g 1 1 0 15 $brassBase
FillRect $g 14 14 0 15 $brassBase
SaveBitmap $g 'transport_registration_terminal_side.png'

# ---------- 3. TOP: black lid with brass border ----------
$g = NewBitmap
FillCase $g
FillRect $g 0 15 0 0 $brassLo
FillRect $g 0 15 15 15 $brassLo
FillRect $g 0 0 0 15 $brassLo
FillRect $g 15 15 0 15 $brassLo
FillRect $g 0 15 1 1 $brassBase
FillRect $g 0 15 14 14 $brassBase
FillRect $g 1 1 0 15 $brassBase
FillRect $g 14 14 0 15 $brassBase
[void]$g.SetPixel(0,0,$brassHi); [void]$g.SetPixel(15,0,$brassHi)
[void]$g.SetPixel(0,15,$brassHi); [void]$g.SetPixel(15,15,$brassHi)
SaveBitmap $g 'transport_registration_terminal_top.png'

# ---------- 4. KEYBOARD: key rows + brass edge strip ----------
$g = NewBitmap
FillCase $g
FillRect $g 0 15 0 0 $gapDark
FillRect $g 0 15 3 3 $gapDark
FillRect $g 0 15 4 4 $gapDark
FillRect $g 0 15 7 7 $gapDark
FillRect $g 0 15 13 13 $gapDark
FillRect $g 0 15 14 14 $brassBase
FillRect $g 0 15 15 15 $brassLo
for ($x = 0; $x -lt 16; $x++) {
    if ($x % 3 -ne 2) {
        [void]$g.SetPixel($x,1,$keyCap); [void]$g.SetPixel($x,2,$keyCapLo)
        [void]$g.SetPixel($x,5,$keyCap); [void]$g.SetPixel($x,6,$keyCapLo)
    } else {
        [void]$g.SetPixel($x,1,$gapDark); [void]$g.SetPixel($x,2,$gapDark)
        [void]$g.SetPixel($x,5,$gapDark); [void]$g.SetPixel($x,6,$gapDark)
    }
}
SaveBitmap $g 'transport_registration_terminal_keyboard.png'

# ---------- 5. CASE: plain black (bottom/back) ----------
$g = NewBitmap
FillCase $g
SaveBitmap $g 'transport_registration_terminal_case.png'

# ---------- 6. BRASS: support plate (foot) ----------
$g = NewBitmap
FillCase $g
for ($x = 0; $x -lt 16; $x++) {
    for ($y = 0; $y -lt 16; $y++) {
        switch (($x * 5 + $y * 11) % 6) {
            0 { [void]$g.SetPixel($x,$y,$brassHi) }
            1 { [void]$g.SetPixel($x,$y,$brassHi) }
            2 { [void]$g.SetPixel($x,$y,$brassBase) }
            5 { [void]$g.SetPixel($x,$y,$brassLo) }
            default { [void]$g.SetPixel($x,$y,$brassBase) }
        }
    }
}
SaveBitmap $g 'transport_registration_terminal_brass.png'
