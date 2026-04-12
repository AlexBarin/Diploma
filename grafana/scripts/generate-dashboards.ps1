param()

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$labRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$dashboardsDir = Join-Path $labRoot "grafana\dashboards"
$postgresVolumeRoot = [System.IO.Path]::GetPathRoot((Resolve-Path (Join-Path $labRoot "data\postgres")).Path)
$postgresDrive = ($postgresVolumeRoot.TrimEnd("\")).TrimEnd(":").ToUpperInvariant()
$windowsHostJob = "windows-host"
$predictiveGroupBy = "series_id, source_service, source_host, source_drive, source_endpoint, source_job_type"

$datasource = [ordered]@{
    type = "prometheus"
    uid  = "prometheus-metric-lab"
}

$dashboardRefs = @(
    [ordered]@{ Title = "App"; Uid = "service-metric-lab-app" },
    [ordered]@{ Title = "Worker"; Uid = "service-metric-lab-worker" },
    [ordered]@{ Title = "Predictive"; Uid = "service-predictive-module" },
    [ordered]@{ Title = "Postgres"; Uid = "service-postgres" },
    [ordered]@{ Title = "Redis"; Uid = "service-redis" },
    [ordered]@{ Title = "Host"; Uid = "service-host" }
)

function New-AnnotationList {
    return [ordered]@{
        builtIn    = 1
        datasource = [ordered]@{
            type = "grafana"
            uid  = "-- Grafana --"
        }
        enable    = $true
        hide      = $true
        iconColor = "rgba(0, 211, 255, 1)"
        name      = "Annotations & Alerts"
        type      = "dashboard"
    }
}

function New-DashboardLinks {
    return @(
        $dashboardRefs | ForEach-Object {
            [ordered]@{
                title    = $_.Title
                type     = "link"
                url      = "/d/$($_.Uid)/$($_.Uid)"
                keepTime = $true
            }
        }
    )
}

function New-HostCpuExpression {
    return '(1 - avg without (core, mode) (rate(windows_cpu_time_total{job="' + $windowsHostJob + '",mode="idle"}[2m]))) * 100'
}

function New-HostMemoryExpression {
    return '(1 - (windows_memory_physical_free_bytes{job="' + $windowsHostJob + '"} / windows_memory_physical_total_bytes{job="' + $windowsHostJob + '"})) * 100'
}

function New-VolumeUsageExpression {
    param(
        [string]$Drive
    )

    return '(1 - (windows_logical_disk_free_bytes{job="' + $windowsHostJob + '",volume="' + $Drive + ':"} / windows_logical_disk_size_bytes{job="' + $windowsHostJob + '",volume="' + $Drive + ':"})) * 100'
}

function New-DriveThroughputExpression {
    param(
        [ValidateSet("read", "write")]
        [string]$Direction,
        [string]$Drive
    )

    $metricName = if ($Direction -eq "read") {
        "windows_logical_disk_read_bytes_total"
    } else {
        "windows_logical_disk_write_bytes_total"
    }

    return 'label_replace(rate(' + $metricName + '{job="' + $windowsHostJob + '",volume="' + $Drive + ':"}[2m]), "drive", "$1", "volume", "([A-Z]):")'
}

function New-ApiLatencyExpression {
    param(
        [string]$Endpoint
    )

    return 'histogram_quantile(0.95, sum by(service, endpoint, le) (rate(lab_http_request_duration_seconds_bucket{job="metric-lab-app",endpoint="' + $Endpoint + '"}[2m]))) * 1000'
}

function New-CheckoutErrorExpression {
    return '(0 * sum by(service, endpoint) (rate(lab_http_requests_total{job="metric-lab-app",endpoint="/api/checkout"}[2m]))) + (100 * sum by(service, endpoint) (rate(lab_http_requests_total{job="metric-lab-app",endpoint="/api/checkout",status_class="5xx"}[2m])) / clamp_min(sum by(service, endpoint) (rate(lab_http_requests_total{job="metric-lab-app",endpoint="/api/checkout"}[2m])), 0.001))'
}

function New-QueueDepthExpression {
    return 'lab_queue_depth{job="metric-lab-app"}'
}

function New-SyntheticMemoryExpression {
    return 'lab_synthetic_memory_bytes{job="metric-lab-app"}'
}

function New-WorkerJobLatencyExpression {
    param(
        [string]$JobType
    )

    return 'histogram_quantile(0.95, sum by(service, job_type, le) (rate(lab_worker_job_duration_seconds_bucket{job="metric-lab-worker",job_type="' + $JobType + '"}[2m]))) * 1000'
}

function New-PredictiveWindowExpression {
    param(
        [string]$MetricName,
        [string]$Selector,
        [string]$ScaleSuffix = ""
    )

    $expression = 'max by (' + $predictiveGroupBy + ') (' + $MetricName + '{' + $Selector + '})'
    if ($ScaleSuffix) {
        return '(' + $expression + ')' + $ScaleSuffix
    }
    return $expression
}

function New-PredictiveForecastExpression {
    param(
        [string]$MetricName,
        [string]$Selector,
        [string]$ScaleSuffix = ""
    )

    $selectorExpression = $MetricName + '{' + $Selector + '}'
    $timestampExpression = 'predictive_forecast_timestamp_seconds{' + $Selector + '}'
    $expression = 'max by (' + $predictiveGroupBy + ') (' + $selectorExpression + ' and on (app,analysis_mode,fallback_used,metric_alias,model_name,series_id,source_container,source_drive,source_endpoint,source_host,source_instance,source_job,source_job_type,source_service,horizon_step) (abs(' + $timestampExpression + ' - time()) < 15))'
    if ($ScaleSuffix) {
        return '(' + $expression + ')' + $ScaleSuffix
    }
    return $expression
}

function New-Target {
    param(
        [string]$RefId,
        [string]$Expression,
        [string]$LegendFormat
    )

    return [ordered]@{
        expr         = $Expression
        interval     = "15s"
        legendFormat = $LegendFormat
        refId        = $RefId
    }
}

function New-WindowPanelOverrides {
    return @(
        [ordered]@{
            matcher    = [ordered]@{
                id      = "byName"
                options = "actual"
            }
            properties = @(
                [ordered]@{ id = "custom.lineWidth"; value = 3 }
            )
        },
        [ordered]@{
            matcher    = [ordered]@{
                id      = "byName"
                options = "baseline"
            }
            properties = @(
                [ordered]@{ id = "custom.lineWidth"; value = 2 }
            )
        },
        [ordered]@{
            matcher    = [ordered]@{
                id      = "byRegexp"
                options = "^(lower|upper)$"
            }
            properties = @(
                [ordered]@{ id = "custom.lineWidth"; value = 1 },
                [ordered]@{ id = "custom.lineStyle"; value = [ordered]@{ dash = @(6, 4); fill = "dash" } }
            )
        },
        [ordered]@{
            matcher    = [ordered]@{
                id      = "byName"
                options = "forecast"
            }
            properties = @(
                [ordered]@{ id = "custom.lineWidth"; value = 2 },
                [ordered]@{ id = "custom.lineStyle"; value = [ordered]@{ dash = @(10, 6); fill = "dash" } },
                [ordered]@{ id = "custom.showPoints"; value = "never" },
                [ordered]@{ id = "custom.spanNulls"; value = $false }
            )
        },
        [ordered]@{
            matcher    = [ordered]@{
                id      = "byRegexp"
                options = "^f\\.(lower|upper)$"
            }
            properties = @(
                [ordered]@{ id = "custom.lineWidth"; value = 1 },
                [ordered]@{ id = "custom.lineStyle"; value = [ordered]@{ dash = @(3, 5); fill = "dash" } },
                [ordered]@{ id = "custom.showPoints"; value = "never" },
                [ordered]@{ id = "custom.spanNulls"; value = $false }
            )
        }
    )
}

function New-TimeSeriesPanel {
    param(
        [int]$Id,
        [string]$Title,
        [System.Collections.IEnumerable]$Targets,
        [string]$Unit,
        [int]$X,
        [int]$Y,
        [int]$Width,
        [int]$Height
    )

    return [ordered]@{
        datasource  = $datasource
        fieldConfig = [ordered]@{
            defaults  = [ordered]@{
                color  = [ordered]@{ mode = "palette-classic" }
                custom = [ordered]@{
                    axisCenteredZero  = $false
                    axisColorMode     = "text"
                    axisPlacement     = "auto"
                    drawStyle         = "line"
                    fillOpacity       = 0
                    gradientMode      = "none"
                    lineInterpolation = "linear"
                    lineWidth         = 2
                    pointSize         = 4
                    scaleDistribution = [ordered]@{ type = "linear" }
                    showPoints        = "never"
                    spanNulls         = $false
                    stacking          = [ordered]@{
                        group = "A"
                        mode  = "none"
                    }
                }
                unit   = $Unit
            }
            overrides = (New-WindowPanelOverrides)
        }
        gridPos     = [ordered]@{
            h = $Height
            w = $Width
            x = $X
            y = $Y
        }
        id          = $Id
        options     = [ordered]@{
            legend  = [ordered]@{
                displayMode = "list"
                placement   = "bottom"
            }
            tooltip = [ordered]@{
                mode = "multi"
                sort = "none"
            }
        }
        targets     = @($Targets)
        title       = $Title
        type        = "timeseries"
    }
}

function New-Dashboard {
    param(
        [string]$Uid,
        [string]$Title,
        [System.Collections.IEnumerable]$Panels
    )

    return [ordered]@{
        annotations          = [ordered]@{ list = @((New-AnnotationList)) }
        editable             = $true
        fiscalYearStartMonth = 0
        graphTooltip         = 0
        id                   = $null
        links                = (New-DashboardLinks)
        liveNow              = $false
        panels               = @($Panels)
        refresh              = "5s"
        schemaVersion        = 39
        style                = "dark"
        tags                 = @("metric-lab", "predictive")
        templating           = [ordered]@{ list = @() }
        time                 = [ordered]@{
            from = "now-90m"
            to   = "now+5m"
        }
        timepicker           = [ordered]@{}
        timezone             = ""
        title                = $Title
        uid                  = $Uid
        version              = 1
        weekStart            = ""
    }
}

function Write-DashboardFile {
    param(
        [string]$FileName,
        [System.Collections.IDictionary]$Dashboard
    )

    $path = Join-Path $dashboardsDir $FileName
    $json = $Dashboard | ConvertTo-Json -Depth 100
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $json, $utf8WithoutBom)
}

function New-StandardPanelSpec {
    param(
        [string]$Title,
        [string]$Unit,
        [string]$ActualExpression,
        [string]$PredictiveSelector,
        [string]$PredictiveScale = ""
    )

    return [ordered]@{
        Title                  = $Title
        Unit                   = $Unit
        ActualExpression       = $ActualExpression
        AverageExpression      = (New-PredictiveWindowExpression -MetricName "predictive_baseline_value" -Selector $PredictiveSelector -ScaleSuffix $PredictiveScale)
        LowerExpression        = (New-PredictiveWindowExpression -MetricName "predictive_lower_band" -Selector $PredictiveSelector -ScaleSuffix $PredictiveScale)
        UpperExpression        = (New-PredictiveWindowExpression -MetricName "predictive_upper_band" -Selector $PredictiveSelector -ScaleSuffix $PredictiveScale)
        ForecastExpression     = (New-PredictiveForecastExpression -MetricName "predictive_forecast_value" -Selector $PredictiveSelector -ScaleSuffix $PredictiveScale)
        ForecastLowerExpression = (New-PredictiveForecastExpression -MetricName "predictive_forecast_lower_band" -Selector $PredictiveSelector -ScaleSuffix $PredictiveScale)
        ForecastUpperExpression = (New-PredictiveForecastExpression -MetricName "predictive_forecast_upper_band" -Selector $PredictiveSelector -ScaleSuffix $PredictiveScale)
    }
}

function New-WindowPanel {
    param(
        [int]$Id,
        [int]$X,
        [int]$Y,
        [System.Collections.IDictionary]$Spec
    )

    return New-TimeSeriesPanel `
        -Id $Id `
        -Title $Spec.Title `
        -Unit $Spec.Unit `
        -X $X `
        -Y $Y `
        -Width 12 `
        -Height 9 `
        -Targets @(
            (New-Target -RefId "A" -Expression $Spec.ActualExpression -LegendFormat "actual"),
            (New-Target -RefId "B" -Expression $Spec.AverageExpression -LegendFormat "baseline"),
            (New-Target -RefId "C" -Expression $Spec.LowerExpression -LegendFormat "lower"),
            (New-Target -RefId "D" -Expression $Spec.UpperExpression -LegendFormat "upper"),
            (New-Target -RefId "E" -Expression $Spec.ForecastExpression -LegendFormat "forecast"),
            (New-Target -RefId "F" -Expression $Spec.ForecastLowerExpression -LegendFormat "f.lower"),
            (New-Target -RefId "G" -Expression $Spec.ForecastUpperExpression -LegendFormat "f.upper")
        )
}

function New-ServiceDashboard {
    param(
        [string]$Uid,
        [string]$Title,
        [System.Collections.IEnumerable]$PanelSpecs
    )

    $panels = @()
    $index = 0
    foreach ($spec in $PanelSpecs) {
        $column = $index % 2
        $row = [math]::Floor($index / 2)
        $x = 12 * $column
        $y = 9 * $row
        $panels += New-WindowPanel -Id ($index + 1) -X $x -Y $y -Spec $spec
        $index += 1
    }

    return New-Dashboard -Uid $Uid -Title $Title -Panels $panels
}

Get-ChildItem -Path $dashboardsDir -Filter "*.json" -ErrorAction SilentlyContinue | Remove-Item -Force

$appPanels = @(
    (New-StandardPanelSpec -Title "Container CPU" -Unit "percent" -ActualExpression 'metric_lab_container_cpu_usage_percent{service="metric-lab-app"}' -PredictiveSelector 'metric_alias="container_cpu_usage_percent",source_service="metric-lab-app"'),
    (New-StandardPanelSpec -Title "Container Memory" -Unit "bytes" -ActualExpression 'metric_lab_container_memory_usage_bytes{service="metric-lab-app"}' -PredictiveSelector 'metric_alias="container_memory_usage_bytes",source_service="metric-lab-app"'),
    (New-StandardPanelSpec -Title "Queue Depth" -Unit "short" -ActualExpression (New-QueueDepthExpression) -PredictiveSelector 'metric_alias="queue_depth",source_service="metric-lab-app"'),
    (New-StandardPanelSpec -Title "Synthetic Memory" -Unit "bytes" -ActualExpression (New-SyntheticMemoryExpression) -PredictiveSelector 'metric_alias="synthetic_memory_mb",source_service="metric-lab-app"' -PredictiveScale ' * 1024 * 1024'),
    (New-StandardPanelSpec -Title "Checkout Error Rate" -Unit "percent" -ActualExpression (New-CheckoutErrorExpression) -PredictiveSelector 'metric_alias="checkout_error_rate_percent",source_service="metric-lab-app",source_endpoint="/api/checkout"'),
    (New-StandardPanelSpec -Title "Latency /api/home" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/home") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/home"'),
    (New-StandardPanelSpec -Title "Latency /api/catalog" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/catalog") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/catalog"'),
    (New-StandardPanelSpec -Title "Latency /api/product" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/product") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/product"'),
    (New-StandardPanelSpec -Title "Latency /api/search" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/search") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/search"'),
    (New-StandardPanelSpec -Title "Latency /api/cart" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/cart") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/cart"'),
    (New-StandardPanelSpec -Title "Latency /api/cart/add" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/cart/add") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/cart/add"'),
    (New-StandardPanelSpec -Title "Latency /api/checkout" -Unit "ms" -ActualExpression (New-ApiLatencyExpression -Endpoint "/api/checkout") -PredictiveSelector 'metric_alias="api_latency_p95_ms",source_service="metric-lab-app",source_endpoint="/api/checkout"')
)

$workerPanels = @(
    (New-StandardPanelSpec -Title "Container CPU" -Unit "percent" -ActualExpression 'metric_lab_container_cpu_usage_percent{service="metric-lab-worker"}' -PredictiveSelector 'metric_alias="container_cpu_usage_percent",source_service="metric-lab-worker"'),
    (New-StandardPanelSpec -Title "Container Memory" -Unit "bytes" -ActualExpression 'metric_lab_container_memory_usage_bytes{service="metric-lab-worker"}' -PredictiveSelector 'metric_alias="container_memory_usage_bytes",source_service="metric-lab-worker"'),
    (New-StandardPanelSpec -Title "Worker Job P95 / checkout" -Unit "ms" -ActualExpression (New-WorkerJobLatencyExpression -JobType "checkout") -PredictiveSelector 'metric_alias="worker_job_p95_ms",source_service="metric-lab-worker",source_job_type="checkout"')
)

$predictivePanels = @(
    (New-StandardPanelSpec -Title "Container CPU" -Unit "percent" -ActualExpression 'metric_lab_container_cpu_usage_percent{service="predictive-module"}' -PredictiveSelector 'metric_alias="container_cpu_usage_percent",source_service="predictive-module"'),
    (New-StandardPanelSpec -Title "Container Memory" -Unit "bytes" -ActualExpression 'metric_lab_container_memory_usage_bytes{service="predictive-module"}' -PredictiveSelector 'metric_alias="container_memory_usage_bytes",source_service="predictive-module"')
)

$postgresPanels = @(
    (New-StandardPanelSpec -Title "Container CPU" -Unit "percent" -ActualExpression 'metric_lab_container_cpu_usage_percent{service="postgres"}' -PredictiveSelector 'metric_alias="container_cpu_usage_percent",source_service="postgres"'),
    (New-StandardPanelSpec -Title "Container Memory" -Unit "bytes" -ActualExpression 'metric_lab_container_memory_usage_bytes{service="postgres"}' -PredictiveSelector 'metric_alias="container_memory_usage_bytes",source_service="postgres"'),
    (New-StandardPanelSpec -Title ("Disk Usage " + $postgresDrive + ":") -Unit "percent" -ActualExpression (New-VolumeUsageExpression -Drive $postgresDrive) -PredictiveSelector ('metric_alias="postgres_volume_usage_percent",source_service="postgres",source_drive="' + $postgresDrive + '"'))
)

$redisPanels = @(
    (New-StandardPanelSpec -Title "Container CPU" -Unit "percent" -ActualExpression 'metric_lab_container_cpu_usage_percent{service="redis"}' -PredictiveSelector 'metric_alias="container_cpu_usage_percent",source_service="redis"'),
    (New-StandardPanelSpec -Title "Container Memory" -Unit "bytes" -ActualExpression 'metric_lab_container_memory_usage_bytes{service="redis"}' -PredictiveSelector 'metric_alias="container_memory_usage_bytes",source_service="redis"')
)

$hostPanels = @(
    (New-StandardPanelSpec -Title "Host CPU" -Unit "percent" -ActualExpression (New-HostCpuExpression) -PredictiveSelector 'metric_alias="host_cpu_usage_percent"'),
    (New-StandardPanelSpec -Title "Host Memory" -Unit "percent" -ActualExpression (New-HostMemoryExpression) -PredictiveSelector 'metric_alias="host_memory_usage_percent"'),
    (New-StandardPanelSpec -Title "SSD C Read" -Unit "binBps" -ActualExpression (New-DriveThroughputExpression -Direction "read" -Drive "C") -PredictiveSelector 'metric_alias="ssd_read_bytes_per_sec",source_drive="C"'),
    (New-StandardPanelSpec -Title "SSD C Write" -Unit "binBps" -ActualExpression (New-DriveThroughputExpression -Direction "write" -Drive "C") -PredictiveSelector 'metric_alias="ssd_write_bytes_per_sec",source_drive="C"'),
    (New-StandardPanelSpec -Title "SSD D Read" -Unit "binBps" -ActualExpression (New-DriveThroughputExpression -Direction "read" -Drive "D") -PredictiveSelector 'metric_alias="ssd_read_bytes_per_sec",source_drive="D"'),
    (New-StandardPanelSpec -Title "SSD D Write" -Unit "binBps" -ActualExpression (New-DriveThroughputExpression -Direction "write" -Drive "D") -PredictiveSelector 'metric_alias="ssd_write_bytes_per_sec",source_drive="D"')
)

$dashboards = @(
    [ordered]@{
        FileName  = "service-metric-lab-app.json"
        Dashboard = (New-ServiceDashboard -Uid "service-metric-lab-app" -Title "Service - metric-lab-app" -PanelSpecs $appPanels)
    },
    [ordered]@{
        FileName  = "service-metric-lab-worker.json"
        Dashboard = (New-ServiceDashboard -Uid "service-metric-lab-worker" -Title "Service - metric-lab-worker" -PanelSpecs $workerPanels)
    },
    [ordered]@{
        FileName  = "service-predictive-module.json"
        Dashboard = (New-ServiceDashboard -Uid "service-predictive-module" -Title "Service - predictive-module" -PanelSpecs $predictivePanels)
    },
    [ordered]@{
        FileName  = "service-postgres.json"
        Dashboard = (New-ServiceDashboard -Uid "service-postgres" -Title "Service - postgres" -PanelSpecs $postgresPanels)
    },
    [ordered]@{
        FileName  = "service-redis.json"
        Dashboard = (New-ServiceDashboard -Uid "service-redis" -Title "Service - redis" -PanelSpecs $redisPanels)
    },
    [ordered]@{
        FileName  = "service-host.json"
        Dashboard = (New-ServiceDashboard -Uid "service-host" -Title "Service - host" -PanelSpecs $hostPanels)
    }
)

foreach ($dashboardSpec in $dashboards) {
    Write-DashboardFile -FileName $dashboardSpec.FileName -Dashboard $dashboardSpec.Dashboard
}
