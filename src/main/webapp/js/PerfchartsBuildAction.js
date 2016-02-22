(function($){
	$(function(){
		var $tag_error_msg =
		$("#tag_error_msg");
		$("#txt_tag").change(function(){
			$tag_error_msg.removeClass("tag_error_msg_error");
			$tag_error_msg.removeClass("tag_error_msg_fine");
			$tag_error_msg.text("Please wait...");
			$.post("updateTags", { tags: this.value}, function(data, status, jqXHR) {
				if (status !== "success"){
					$tag_error_msg.addClass("tag_error_msg_error");
					$tag_error_msg.text("Failed");
					return;
				}
				if (data.error !== 0) {
					$tag_error_msg.addClass("tag_error_msg_error");
					$tag_error_msg.text(data.errorMessage);
					return;
				}
				$tag_error_msg.addClass("tag_error_msg_fine");
				$tag_error_msg.text(data.errorMessage);
				}).fail(function() {
					$tag_error_msg.addClass("tag_error_msg_error");
					$tag_error_msg.text("Failed.");
					});
			});
			var $sel_build = $("#sel_build");
			$.post("getDestBuilds", function(data, status, jqXHR) {
				if (status === "success" && data.error === 0){
					for (var i = 0; i < data.result.length; ++i) {
						var item = data.result[i];
						$("<option/>").text(item.text).attr("value", item.value).appendTo($sel_build);
					}
				}
			});
			$sel_build.change(function() {
				var selectedBuildNumber = $(this).prop("value");
				if (selectedBuildNumber)
					//location.href = ;
					window.open("comparisonReport/" + selectedBuildNumber + "/monoReport");
			});
		//add the action for startOffset related setting
		var $startOffset_error_msg = $("#startOffset_error_msg")
		$("#btn_generate_time").click(function(){
                        $startOffset_error_msg.removeClass("startOffset_error_msg_error");
			$startOffset_error_msg.removeClass("startOffset_error_msg_fine");

			$.post("startOffset", { startOffset: $("#startOffset").val(), testDuration: $("#testDuration").val() }, function(data, status, jqXHR) {
				if (status !== "success"){
					$startOffset_error_msg.addClass("startOffset_error_msg_error");
					$startOffset_error_msg.text("Numberic setting are need.");
					return;
				}
				if (data.error !== 0) {
					$startOffset_error_msg.addClass("startOffset_error_msg_error");
					$startOffset_error_msg.text(data.errorMessage);
					return;
				}
                                   location.reload(true);
				}).fail(function() {
					$startOffset_error_msg.addClass("startOffset_error_msg_error");
					$startOffset_error_msg.text("Failed.");
					});
				

			});
			

	});
})(jQuery);
