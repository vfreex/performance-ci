(function($){
    $(function(){
        var $tag_error_msg = $("#tag_error_msg");
        var $txt_builds = $("#txt_builds");
        var $btn_generate_trend = $("#btn_generate_trend");
        var $btn_refresh_trend = $("#btn_refresh_trend");
        $btn_generate_trend.click(function(){
            $tag_error_msg.removeClass("tag_error_msg_error");
            $tag_error_msg.removeClass("tag_error_msg_fine");
            $tag_error_msg.text("Please wait...");
            $.post("../../submit", { builds : $txt_builds.val() }, function(data, status, jqXHR) {
                if (status !== "success") {
                    $tag_error_msg.addClass("tag_error_msg_error");
                    $tag_error_msg.text("Failed.");
                    return;
                }
                if (data.error !== 0) {
                    $tag_error_msg.addClass("tag_error_msg_error");
                    $tag_error_msg.text(data.errorMessage);
                    return;
                }
                $tag_error_msg.addClass("tag_error_msg_fine");
                $tag_error_msg.text(data.errorMessage);
                location.href = "../../" + data.url;
            }).fail(function() {
                $tag_error_msg.addClass("tag_error_msg_error");
                $tag_error_msg.text("Failed.");
            });
        });
        $btn_refresh_trend.click(function(){
                    $tag_error_msg.removeClass("tag_error_msg_error");
                    $tag_error_msg.removeClass("tag_error_msg_fine");
                    $tag_error_msg.text("Please wait...");
                    $.post("refresh", function(data, status, jqXHR) {
                        if (status !== "success") {
                            $tag_error_msg.addClass("tag_error_msg_error");
                            $tag_error_msg.text("Failed.");
                            return;
                        }
                        if (data.error !== 0) {
                            $tag_error_msg.addClass("tag_error_msg_error");
                            $tag_error_msg.text(data.errorMessage);
                            return;
                        }
                        $tag_error_msg.addClass("tag_error_msg_fine");
                        $tag_error_msg.text(data.errorMessage);
                        location.reload(true);
                    }).fail(function() {
                        $tag_error_msg.addClass("tag_error_msg_error");
                        $tag_error_msg.text("Failed.");
                    });
                });

    });
})(jQuery);