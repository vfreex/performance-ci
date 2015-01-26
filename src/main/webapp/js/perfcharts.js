ChartGeneration = function() {
};
ChartGeneration.data = [];
ChartGeneration.compositeReport = {};
(function($) {
	function setupUI() {
		// set up tooltip
		$("<div id='tooltip'></div>").css({
			position : "absolute",
			display : "none",
			border : "1px solid #fdd",
			padding : "2px",
			"background-color" : "#fee",
			opacity : 0.80
		}).appendTo("body");
		// set up nav
		$("h1").text("Perf-test Report");
		var data = ChartGeneration.data;
		var $report_nav = $("#report_nav");
		for (var i = 0; i < data.length; ++i) {
			var report = data[i];
			var $a = $("<a class='report_switch' href='javascript:'/>").text(
					report.title).data("report-index", i).click(function() {
				$(".control_pad").html("");
				showReport($(this).data("report-index"));
			});
			$report_nav.append($("<li/>").append($a));
		}
		$("<li/>")
				.append(
						$(
								"<a class='report_switch' href='javascript:'>Composite Chart</a>")
								.click(function() {
									showCompositeReport();
								})).appendTo($report_nav);
	}

	function showReport(index) {
		var data = ChartGeneration.data;
		if (index >= data.length)
			return;
		drawReport($(".report"), data[index]);
	}

	function showCompositeReport() {
		$(".control_pad").html("");
		var report = ChartGeneration.compositeReport;
		report.title = "";
		if (!report.charts) {
			report.charts = [ {
				title : "Composite Chart",
				subtitle : "",
				xaxisMode : "TIME",
				series : [],
				yaxes : [],
				yaxesMap : {},
			} ];
		}
		drawReport($(".report"), report);
		var $control_pad = $(".control_pad");
		var $placeholder = $(".placeholder");
		$(".chart").addClass("composite_chart");
		setupControlPadForCompositeChart($placeholder, $control_pad);
	}

	function on_cb_control_pad_change() {
		var $this = $(this);
		var tag = $this.data("tag");
		var compositeChart = ChartGeneration.compositeReport.charts[0];
		var series = compositeChart.series;
		var yaxesMap = compositeChart.yaxesMap;
		var yaxes = compositeChart.yaxes;
		var line = tag.line;
		if ($this.is(":checked")) {
			var yLabel = line._unit && line._unit.value ? tag.chart.yLabel
					+ " (" + line._unit.value + ")" : tag.chart.yLabel
			if (!yaxesMap[yLabel]) {
				yaxes.push({
					position : yaxes.length & 1 ? "right" : "left",
					axisLabel : yLabel
				});
				yaxesMap[yLabel] = yaxes.length;
			}
			series.push({
				// label : tag.report.title + "<br/>" + tag.chart.title
				// + "<br/>" + line.label,
				label : tag.chart.subtitle ? tag.chart.subtitle + "-"
						+ line.label : line.label,
				data : line.data,
				yaxis : yaxesMap[yLabel],
				color : tag.index
			});
		} else {
			for (var i = 0; i < series.length; ++i) {
				if (series[i].data === line.data) {
					series.splice(i, 1);
					break;
				}
			}
		}
		draw(tag.$placeholder, tag.$legend, compositeChart, function(options) {
			options.yaxes = yaxes;
			return options;
		});
	}

	function on_clearSelection_click() {
		var $this = $(this);
		var $placeholder = $this.data("$placeholder");
		var compositeChart = ChartGeneration.compositeReport.charts[0];
		compositeChart.series = [];
		$(".cb_control_pad").prop("checked", false);
		redraw($placeholder, $placeholder.data("plot"), compositeChart);
	}

	function setupControlPadForCompositeChart($placeholder, $control_pad) {
		var reports = ChartGeneration.data;
		var $topDiv = $("<div/>").appendTo($control_pad);
		$("<button>Clear Selection</button>")
				.data("$placeholder", $placeholder).click(
						on_clearSelection_click).appendTo($topDiv);
		$("<input type='text' placeholder='title'/>").change(
				function() {
					$(".composite_chart .chart_title").text(
							this.value ? this.value
									: ChartGeneration.compositeReport.title);
				}).appendTo($topDiv);
		$("<input type='text' placeholder='subtitle' />")
				.change(
						function() {
							$(".composite_chart .chart_subtitle")
									.text(
											this.value ? this.value
													: ChartGeneration.compositeReport.subtitle);
						}).appendTo($topDiv);
		var $select = $("<select><option value='TIME'>time</option><option value='INTEGER'>integer</option></select>")
				.change(
						function() {
							var compositeChart = ChartGeneration.compositeReport.charts[0];
							compositeChart.xaxisMode = this.value;
							showCompositeReport();
						}).appendTo($topDiv);
		$select.val(ChartGeneration.compositeReport.charts[0].xaxisMode);

		var $rootList = $("<dl/>").appendTo($control_pad);
		var indexOfSeries = 0;
		for (var i = 0; i < reports.length; ++i) {
			var report = reports[i];
			$("<dt/>").text(report.title).appendTo($rootList);
			var charts = report.charts;
			for (var j = 0; j < charts.length; ++j) {
				var chart = charts[j];
				if (chart.xaxisMode === "CATEGORIES")
					continue;
				if (chart.xaxisMode === "INTEGER")
					ChartGeneration.compositeReport.xaxisMode = null;
				var $chartList = $("<dl/>");
				$("<dd/>").append($chartList).appendTo($rootList);
				$("<dt/>").text(chart.title).appendTo($chartList);
				var series = chart.series;
				for (var k = 0; k < series.length; ++k) {
					var line = series[k];
					var $checkbox = $(
							"<input type='checkbox' class='cb_control_pad'/>")
							.data("tag", {
								report : report,
								chart : chart,
								line : line,
								$placeholder : $placeholder,
								$legend : $placeholder.nextAll(".legend"),
								index : indexOfSeries++
							}).change(on_cb_control_pad_change);
					$("<dd/>").append(
							$("<label/>").text(line.label).prepend($checkbox))
							.appendTo($chartList);
				}
			}

		}
	}

	function drawReport($container, report) {
		$container.children(".report_title").text(report.title);
		var $charts = $container.children(".charts");
		var charts = report.charts;
		$charts.html("");
		for (var i = 0; i < charts.length; ++i) {
			var chart = charts[i];
			var $chart = $("<div class='chart'/>");
			$charts.append($chart);
			if (chart.chartType === "JmeterSummaryChart")
				drawJmeterSummaryChart($chart, chart);
			else
				drawChart($chart, chart);
		}
	}

	function drawJmeterSummaryChart($chart, chart) {
		$chart.append($("<h3 class='chart_title'/>").text(chart.title)).append(
				$("<h4 class='chart_subtitle'/>").text(chart.subtitle));
		var $table = $("<table class='chart_table JmeterSummaryChart' />")
				.appendTo($chart);
		var $tableHeaderRow = $("<tr/>").appendTo(
				$("<thead/>").appendTo($table));
		for (var i = 0; i < chart.columnLabels.length; ++i) {
			$("<th/>").text(chart.columnLabels[i]).appendTo($tableHeaderRow);
		}
		var $tbody = $("<tbody/>").appendTo($table);
		for (var i = 0; i < chart.rows.length; ++i) {
			var $tableRow = $("<tr/>").appendTo($tbody);
			for (var j = 0; j < chart.rows[i].length; ++j) {
				var val = chart.rows[i][j];
				var $td = $("<td/>").text(val).appendTo($tableRow);
				if (j == 7 && val > 0) {
					$td.addClass("JmeterSummaryChart_error");
				}
			}
		}
	}
	function drawChart($chart, chart) {
		$chart.append($("<h3 class='chart_title'/>").text(chart.title)).append(
				$("<h4 class='chart_subtitle'/>").text(chart.subtitle));
		var $placeholder = $("<div class='placeholder'/>").appendTo($chart);
		$("<div class='x_label'/>").text(chart.xLabel).appendTo($chart);
		var $legend = $("<div class='legend'/>").appendTo($chart);
		var plot = draw($placeholder, $legend, chart);
		registerEvents($placeholder);
		var $category_tick = $placeholder.find('.category_tick');
		var maxTickLabelHeight = 0;
		$category_tick.each(function(_, elem) {
			var $this = $(elem);
			if (maxTickLabelHeight < $this.width())
				maxTickLabelHeight = $this.width();
			$this.css("margin-top", $this.width());
			$this.css("margin-left", $this.height());
		});
		$placeholder.css("padding-bottom", maxTickLabelHeight);
	}
	function yTickFormatter(num, _) {
		var r = num;
		var absNum = Math.abs(num);
		var rounded = Math.round(num);
		var d = Math.abs(num - rounded);
		if (absNum != 0 && (absNum >= 1e6 || num <= 1e-5))
			r = num.toExponential();
		else if (d >= 1e-5)
			r = num.toFixed(1);
		else if (d > 0)
			r = rounded;
		return r;
	}
	function draw($placeholder, $legend, chart, optionsHook) {
		if (!chart.yaxes) {
			chart.yaxes = [];
			chart.yaxesMap = [];
			for (var i = 0; i < chart.series.length; ++i) {
				var line = chart.series[i];
				// var yLabel = line._unit && line._unit.value ? chart.yLabel
				// + " / " + line._unit.value : chart.yLabel;
				var yLabel = line._unit && line._unit.value ? line._unit.value
						: chart.yLabel;
				if (!chart.yaxesMap[yLabel]) {
					chart.yaxes.push({
						position : chart.yaxes.length & 1 ? "right" : "left",
						axisLabel : yLabel
					});
					line.yaxis = chart.yaxesMap[yLabel] = chart.yaxes.length;
				} else {
					line.yaxis = chart.yaxesMap[yLabel];
				}
			}
		}

		var options = {
			yaxes : chart.yaxes,
			yaxis : {
				axisLabel : chart.yLabel,
				minTickSize : 0.1,
				tickFormatter : yTickFormatter
			},
			legend : {
				position : "nw",
				margin : [ 0, 0 ],
				noColumns : 5,
				container : $legend
			},
			selection : {
				mode : "xy"
			},
			series : {
				lines : {
					show : true
				},
				points : {
					show : false
				}
			},
			crosshair : {
				mode : "x"
			},
			grid : {
				hoverable : true
			}
		};
		if (optionsHook)
			options = optionsHook(options);
		switch (chart.xaxisMode) {
		case "TIME":
			options.xaxis = {
				mode : "time"
			};
			break;
		case "CATEGORIES":
			var map = [];
			for (var i = 0; i < chart.series.length; ++i) {
				var seriesData = chart.series[i].data[0];
				seriesData[0] = i;
				map.push(chart.series[i].label);
			}
			options.xaxis = {
				minTickSize : 1,
				tickSize : 1,
				tickLength : 0,
				tickFormatter : function(num, _) {
					var newTick = map[num];
					return newTick ? "<div class='category_tick'>" + map[num]
							+ "</div>" : "";
				}
			}
			break;
		case "NUMBER":
			break;
		case "INTEGER":
			options.xaxis = {
				minTickSize : 1,
				tickSize : 1,
				tickFormatter : function(num, _) {
					return Math.round(num);
				}
			}
			break;
		default:
			break;
		}
		// plot
		var plot = $.plot($placeholder, chart.series, options);
		$placeholder.data("plot", plot);
		return plot;
	}

	function registerEvents($placeholder) {
		$placeholder.off();
		// zooming
		$placeholder.bind("plotselected", function(event, ranges) {
			var plot = $placeholder.data("plot");
			// if (true) {
			var xaxes = plot.getXAxes();
			for (var i = 0; i < xaxes.length;) {
				var opts = xaxes[i++].options;
				var r = i > 1 ? ranges["x" + i + "axis"] : ranges.xaxis;
				if (!r)
					continue;
				opts.min = r.from;
				opts.max = r.to;
			}
			var yaxes = plot.getYAxes();
			for (var i = 0; i < yaxes.length;) {
				var opts = yaxes[i++].options;
				var r = i > 1 ? ranges["y" + i + "axis"] : ranges.yaxis;
				if (!r)
					continue;
				opts.min = r.from;
				opts.max = r.to;
			}
			redraw($placeholder, plot);
			plot.clearSelection();
			// }
		});
		// tooltip
		$placeholder.bind("plothover", function(event, pos, item) {
			var plot = $placeholder.data("plot");
			if (item) {
				var x = item.series.xaxis.options.mode === "time" ? new Date(
						item.datapoint[0]).toUTCString() : item.datapoint[0];
				var y = item.datapoint[1].toFixed(2);
				if (item.series._unit && item.series._unit.value)
					y += " " + item.series._unit.value;
				$("#tooltip").html(
						"<b>" + item.series.label + "</b><br />" + x + "<br />"
								+ y).css({
					top : item.pageY + 5,
					left : item.pageX + 5
				}).fadeIn(200);
			} else {
				$("#tooltip").hide();
			}
		});
		// reset zooming
		$placeholder.bind("dblclick", function() {
			resetZooming($placeholder);
		});
	}

	function redraw($placeholder, plot, data, ignoreGridUpdate) {
		if (data)
			plot.setData(data.series);
		if (!ignoreGridUpdate)
			plot.setupGrid();
		plot.draw();
		$placeholder.find('.category_tick').each(function(_, elem) {
			var $this = $(elem);
			$this.css("margin-top", $this.width());
			$this.css("margin-left", $this.height());
		});
	}

	function resetZooming($placeholder) {
		var plot = $placeholder.data("plot");
		$.each(plot.getXAxes(), function(_, axis) {
			var opts = axis.options;
			opts.min = opts.max = null;
		});
		$.each(plot.getYAxes(), function(_, axis) {
			var opts = axis.options;
			opts.min = opts.max = null;
		});
		redraw($placeholder, plot, null);
	}

	$(function() {
		setupUI();
		showReport(0);
		// showCompositeReport();
	});

})(jQuery);