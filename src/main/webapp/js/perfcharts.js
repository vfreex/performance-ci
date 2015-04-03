function ChartGeneration($) {
	ChartGeneration.eventHandlers = {
		toggleSeries : function(sender, reportIndex, chartIndex, seriesIndex) {
			if (reportIndex < 0) // composite report does not support this
				// function
				return;
			var chart = ChartGeneration.data[reportIndex].charts[chartIndex];
			var series = chart.series[seriesIndex];
			var show = false;
			if (series._show === false)
				show = true;
			series._show = show;

			if (!series.lines)
				series.lines = {
					show : true
				};
			series.lines.show = show ? series._showLines : false;

			if (!series.bars)
				series.bars = {
					show : false
				};
			series.bars.show = show ? series._showBars : false;

			if (!series.points)
				series.points = {
					show : false
				};
			series.points.show = show ? series._showPoints : false;

			redraw(chart.plot.getPlaceholder(), chart.plot, chart, true);
			// sender.style.color = show ? '#F00' : '#0F0'; // does not work,
			// because the clicked legend has been discarded after redrawing.
			// use css style instead.
		}
	};
	ChartGeneration.data = [];
	ChartGeneration.compositeReport = {};

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
		data[index]._index = index;
		drawReport($(".report"), data[index]);
	}

	function showCompositeReport() {
		$(".control_pad").html("");
		var report = ChartGeneration.compositeReport;
		report.title = "";
		report._index = -1;
		if (!report.charts) {
			report.charts = [ {
				title : "Composite Chart",
				subtitle : "",
				xaxisMode : "TIME"
			} ];
		}
		var compositeChart = report.charts[0];
		compositeChart.series = [];
		compositeChart.yaxes = [];
		compositeChart.yaxesMap = {};
		compositeChart.xaxisTicks = null;
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
		var yLabel = line._unit && line._unit.value ? tag.chart.yLabel + " ("
				+ line._unit.value + ")" : tag.chart.yLabel;
		var useWhichYaxis = yaxesMap[yLabel]; // starts from 1 if exists
		if ($this.is(":checked")) {
			if (!useWhichYaxis) {
				yaxes.push({
					position : yaxes.length & 1 ? "right" : "left",
					axisLabel : yLabel,
					_used : 0
				});
				useWhichYaxis = yaxesMap[yLabel] = yaxes.length;
			}
			++yaxes[useWhichYaxis - 1]._used;
			series.push({
				label : tag.chart.subtitle ? tag.chart.subtitle + "-"
						+ line.label : line.label,
				data : line.data,
				yaxis : useWhichYaxis,
				color : tag.index
			});
			compositeChart.xaxisMode = tag.chart.xaxisMode;
			if (compositeChart.xaxisMode === "INTEGER") {
				compositeChart.xaxisTicks = tag.chart.xaxisTicks;
			}
		} else {
			for (var i = 0; i < series.length; ++i) {
				if (series[i].data === line.data) {
					series.splice(i, 1);
					break;
				}
			}
			if (--yaxes[useWhichYaxis - 1]._used <= 0) {
				for (var i = useWhichYaxis; i < yaxes.length; ++i) {
					yaxes[i].position = yaxes[i].position == "right" ? "left"
							: "right";
					--yaxesMap[yaxes[i].axisLabel]
					yaxes[i - 1] = yaxes[i];
				}
				yaxesMap[yLabel] = 0;
				yaxes.pop();
				for (var i = 0; i < series.length; ++i) {
					if (series[i].yaxis > useWhichYaxis)
						--series[i].yaxis;
				}
			}
		}
		draw(tag.$placeholder, tag.$legend, compositeChart, function(options) {
			options.yaxesMap = yaxesMap;
			options.yaxes = yaxes;
			return options;
		});
	}

	function on_clearSelection_click() {
		var $this = $(this);
		var $placeholder = $this.data("$placeholder");
		var compositeChart = ChartGeneration.compositeReport.charts[0];
		compositeChart.series = [];
		compositeChart.yaxes = [];
		compositeChart.yaxesMap = {};
		compositeChart.xaxisTicks = null;
		$(".cb_control_pad").prop("checked", false);
		redraw($placeholder, $placeholder.data("plot"), compositeChart);
	}

	function setupControlPadForCompositeChart($placeholder, $control_pad) {
		var reports = ChartGeneration.data;
		var $topDiv = $("<div/>").appendTo($control_pad);
		$("<button>Clear Selection</button>")
				.data("$placeholder", $placeholder).click(
						on_clearSelection_click).appendTo($topDiv);
		$("<input type='text' placeholder='caption'/>").change(
				function() {
					$(".composite_chart .chart_title").text(
							this.value ? this.value
									: ChartGeneration.compositeReport.title);
				}).appendTo($topDiv);
		$("<input type='text' placeholder='description' />")
				.change(
						function() {
							$(".composite_chart .chart_subtitle")
									.text(
											this.value ? this.value
													: ChartGeneration.compositeReport.subtitle);
						}).appendTo($topDiv);
		/*
		 * var $select = $("<select><option value='TIME'>time</option><option
		 * value='INTEGER'>integer</option></select>") .change( function() {
		 * var compositeChart = ChartGeneration.compositeReport.charts[0];
		 * compositeChart.xaxisMode = this.value; showCompositeReport();
		 * }).appendTo($topDiv);
		 * $select.val(ChartGeneration.compositeReport.charts[0].xaxisMode);
		 */

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
				if (!series)
					continue;
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
			chart._index = i;
			chart._reportIndex = report._index;
			if (chart.chartType === "JmeterSummaryChart")
				drawJmeterSummaryChart($chart, chart);
			else if (chart.chartType === "TABLE")
				drawTable($chart, chart);
			else
				drawChart($chart, chart);
		}
	}

	function drawTable($chart, chart) {
		$chart.append($("<h3 class='chart_title'/>").text(chart.title)).append(
				$("<h4 class='chart_subtitle'/>").text(chart.subtitle));
		var $table = $("<table class='chart_table'/>").appendTo($chart);
		if (chart.key)
			$table.attr("data-key", chart.key);

		var $tableHeaderRow = $("<tr/>").appendTo(
				$("<thead/>").appendTo($table));
		for (var i = 0; i < chart.header.length; ++i) {
			$("<th/>").text(chart.header[i]).appendTo($tableHeaderRow);
		}
		var $tbody = $("<tbody/>").appendTo($table);
		for (var i = 0; i < chart.rows.length; ++i) {
			var $tableRow = $("<tr/>").appendTo($tbody);
			var row = chart.rows[i];
			for (var j = 0; j < row.length; ++j) {
				var cell = row[j];
				var isFloat = cell.valueType === "double"
						|| cell.valueType === "float";
				var isNumber = isFloat || typeof cell.value === "number"
						|| cell.valueType === "int"
						|| cell.valueType === "long";
				var showText = cell.value;
				if (isFloat) {
					showText = cell.value !== null ? cell.value.toFixed(3)
							: (cell.rawValue === "NaN" ? "N/A" : cell.rawValue);
				}
				var $td = $("<td/>").text(showText).appendTo($tableRow);
				if (cell.cssClass)
					$td.addClass(cell.cssClass);
				if (isNumber)
					$td.css("text-align", "right");
			}
		}
		$table.tablesorter().stickyTableHeaders();
	}
	// will be deprecated 
	function drawJmeterSummaryChart($chart, chart) {
		$chart.append($("<h3 class='chart_title'/>").text(chart.title)).append(
				$("<h4 class='chart_subtitle'/>").text(chart.subtitle));
		var $table = $("<table class='chart_table JmeterSummaryChart'/>")
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
				if (j > 0) {
					if (j === 7 && val > 0) {
						$td.addClass("JmeterSummaryChart_error");
					} else if (j === 2) {
						var fval = parseFloat(val);
						if (isNaN(fval) || fval >= 5000)
							$td.addClass("JmeterSummaryChart_error");
					}
				}
			}
		}
		$table.tablesorter();
	}
	function drawChart($chart, chart) {
		if (!chart._prepared) {
			chart._prepared = true;
			// add index to each series
			for (var i = 0; i < chart.series.length; ++i) {
				var series = chart.series[i];
				series._index = i;
				series._chartIndex = chart._index;
				series._reportIndex = chart._reportIndex;
				series._showLines = series.lines && series.lines.show === false ? false
						: true;
				series._showBars = series.bars && series.bars.show === true ? true
						: false;
				series._showPoints = series.points
						&& series.points.show === true ? true : false;
				// series._show = true;
			}
		}
		$chart.append($("<h3 class='chart_title'/>").text(chart.title)).append(
				$("<h4 class='chart_subtitle'/>").text(chart.subtitle));
		var $placeholder = $("<div class='placeholder'/>").appendTo($chart);
		$("<div class='x_label'/>").text(chart.xLabel).appendTo($chart);
		var $legend = $("<div class='legend'/>").appendTo($chart);
		var plot = draw($placeholder, $legend, chart);
		registerEvents($placeholder);
		// var $category_tick = $placeholder.find('.category_tick');
		// var maxTickLabelHeight = 0;
		// $category_tick.each(function(_, elem) {
		// var $this = $(elem);
		// if (maxTickLabelHeight < $this.width())
		// maxTickLabelHeight = $this.width();
		// $this.css("margin-top", $this.width());
		// $this.css("margin-left", $this.height());
		// });
		// $placeholder.css("padding-bottom", maxTickLabelHeight);
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
				container : $legend,
				labelFormatter : labelFormatter
			},
			selection : {
				mode : "xy"
			},
			series : {
				lines : {
					show : true
				},
				points : {
					show : true,
					radius : 1,
					lineWidth : 0,
					fill : 1,
					fillColor : false
				}
			},
			shadowSize : 0,
			crosshair : {
				mode : "xy"
			},
			grid : {
				hoverable : true
			}
		/*
		 * , pan: { interactive: true }, zoom: { interactive: true }
		 */
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
			options.series.bars = {
				show : true,
				barWidth : 0.8
			}
			options.series.points.show = false;
			break;
		case "NUMBER":
			break;
		case "INTEGER":
			options.xaxis = {
				minTickSize : 1,
				tickSize : 1
			/*
			 * tickFormatter : function(num, _) { return Math.round(num); }
			 */
			}
			if (chart.xaxisTicks) {
				options.xaxis.ticks = chart.xaxisTicks;
			} else {
				options.xaxis.tickFormatter = function(num, _) {
					return Math.round(num);
				};
			}
			break;
		default:
			break;
		}
		// plot
		var plot = $.plot($placeholder, chart.series, options);
		$placeholder.data("plot", plot);
		$placeholder.data("chart", chart);
		chart.plot = plot;
		return plot;
	}

	function labelFormatter(label, series) {
		var show = true;
		if (series._show === false)
			show = false;
		return '<a class="'
				+ (show ? 'series_label_shown' : 'series_label_hidden')
				+ '" onclick="ChartGeneration.eventHandlers.toggleSeries(this, '
				+ series._reportIndex + ", " + series._chartIndex + ", "
				+ series._index + ');">' + label + '</a>';
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
			redraw($placeholder, plot, $placeholder.data("chart"));
			plot.clearSelection();
			// }
		});

		// tooltip
		$placeholder
				.bind(
						"plothover",
						function(event, pos, item) {
							// var plot = $placeholder.data("plot");
							var chart = $placeholder.data("chart");
							if (item) {
								var x = item.series.xaxis.options.mode === "time" ? new Date(
										item.datapoint[0]).toUTCString()
										: item.datapoint[0];
								if (chart.xaxisTicks) {
									var tickLabel = binary_search_for_tick(
											chart.xaxisTicks, x);
									if (tickLabel)
										x = tickLabel;
								}

								var y = item.datapoint[1].toFixed(3);
								if (item.series._unit
										&& item.series._unit.value)
									y += " " + item.series._unit.value;
								$("#tooltip").html(
										"<b>" + item.series.label
												+ "</b><br />" + x + "<br />"
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

	function binary_search_for_tick(ticks, x) {
		var l = 0, r = ticks.length - 1;
		while (l <= r) {
			var mid = l + ((r - l) >> 1);
			if (ticks[mid][0] == x)
				return ticks[mid][1];
			if (ticks[mid][0] < x)
				l = mid + 1;
			else
				r = mid - 1;
		}
		return null;
	}

	function redraw($placeholder, plot, chart, ignoreGridUpdate) {
		if (chart)
			plot.setData(chart.series);
		if (!ignoreGridUpdate)
			plot.setupGrid();
		plot.draw();
		// if (chart && chart.xaxisMode === "CATEGORIES"){
		// $placeholder.find('.category_tick').each(function(_, elem) {
		// var $this = $(elem);
		// $this.css("margin-top", $this.width());
		// $this.css("margin-left", $this.height());
		// });
		// }
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
	});

}
ChartGeneration(jQuery);
