Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
foreach ($corpus in @('small','medium','large')) {
    $rows = Import-Csv (Join-Path $PSScriptRoot "results/$corpus/summary.csv")
    $names = @('synchronized-map','concurrent-merge','long-adder','local-maps')
    foreach ($metric in @('time','speedup','efficiency')) {
        $field = @{time='median_seconds';speedup='speedup';efficiency='efficiency_percent'}[$metric]
        $label = @{time='Time (seconds)';speedup='Speedup (Tseq / Tp)';efficiency='Efficiency (%)'}[$metric]
        $max = (($rows | Where-Object variant -In $names | ForEach-Object { [double]::Parse($_.$field,[cultureinfo]::InvariantCulture) }) | Measure-Object -Maximum).Maximum * 1.12
        $bmp = New-Object Drawing.Bitmap 1100,700
        $g = [Drawing.Graphics]::FromImage($bmp)
        $g.Clear([Drawing.Color]::White)
        $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $font = New-Object Drawing.Font 'Arial',12
        $title = New-Object Drawing.Font 'Arial',20
        $g.DrawString("$corpus - $label",$title,[Drawing.Brushes]::Black,80,20)
        for ($i=0; $i -le 5; $i++) {
            $y = 560 - $i*95
            $g.DrawLine([Drawing.Pens]::LightGray,90,$y,1040,$y)
            $g.DrawString(($max*$i/5).ToString('G3',[cultureinfo]::InvariantCulture),$font,[Drawing.Brushes]::Black,5,($y-10))
        }
        $pset = @(1,2,4,8,16,32,64)
        for ($i=0; $i -lt 7; $i++) { $g.DrawString([string]$pset[$i],$font,[Drawing.Brushes]::Black,(90+$i*155),575) }
        $g.DrawString('Threads (log2 spacing)',$font,[Drawing.Brushes]::Black,420,610)
        $colors = @([Drawing.Color]::RoyalBlue,[Drawing.Color]::DarkOrange,[Drawing.Color]::ForestGreen,[Drawing.Color]::DarkViolet)
        for ($j=0; $j -lt 4; $j++) {
            $points = @($rows | Where-Object variant -EQ $names[$j] | ForEach-Object {
                $i = [Array]::IndexOf($pset,[int]$_.threads)
                $v = [double]::Parse($_.$field,[cultureinfo]::InvariantCulture)
                New-Object Drawing.PointF ([single](90+$i*155)),([single](560-475*$v/$max))
            })
            $pen = New-Object Drawing.Pen $colors[$j],3
            $brush = New-Object Drawing.SolidBrush $colors[$j]
            $g.DrawLines($pen,[Drawing.PointF[]]$points)
            foreach ($pt in $points) { $g.FillEllipse($brush,($pt.X-4),($pt.Y-4),8,8) }
            $g.DrawString($names[$j],$font,$brush,(55+$j*265),655)
            $pen.Dispose(); $brush.Dispose()
        }
        $out = Join-Path $PSScriptRoot "results/$corpus/$metric.png"
        $bmp.Save($out,[Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose(); $font.Dispose(); $title.Dispose()
        if ($corpus -eq 'large') { Copy-Item -LiteralPath $out -Destination (Join-Path $PSScriptRoot "results/$metric.png") }
    }
}
