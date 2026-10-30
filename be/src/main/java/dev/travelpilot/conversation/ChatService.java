package dev.travelpilot.conversation;

import dev.travelpilot.ask.AskResponse;
import dev.travelpilot.ask.AskService;
import dev.travelpilot.knowledge.Source;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRepository;
import dev.travelpilot.trip.TripService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One chat turn on a trip: route the intent, then answer with RAG (ASK_QUESTION), re-plan part of the trip
 * (MODIFY_PLAN) or reply with a fixed text (SMALL_TALK / OUT_OF_SCOPE, no model call). Both messages are stored.
 */
@Service
public class ChatService {

    static final String SMALL_TALK_REPLY = "Chào bạn! Mình có thể trả lời câu hỏi về các điểm trong lịch (giờ mở cửa, "
            + "giá vé, có hợp với trẻ em không…) hoặc đổi lịch, ví dụ: \"Ngày 2 trời mưa, đổi sang hoạt động trong nhà\" "
            + "hay \"Giảm ngân sách còn 3 triệu\".";
    static final String OUT_OF_SCOPE_REPLY = "Xin lỗi, mình chỉ hỗ trợ chuyến đi này: lịch trình, địa điểm, ăn uống, "
            + "đi lại và thời tiết ở Hà Nội, Đà Nẵng (gồm Hội An) và Quảng Ninh. Bạn muốn hỏi hay đổi gì trong lịch không?";
    static final String NOT_UNDERSTOOD_REPLY = "Mình chưa rõ bạn muốn đổi gì trong lịch. Bạn có thể nói cụ thể hơn, "
            + "ví dụ: \"Ngày 2 đổi sang hoạt động trong nhà\", \"Bỏ Bà Nà\" hoặc \"Giảm ngân sách còn 3 triệu\".";

    /** Reply of one turn; {@code plan} and {@code newVersion} are set when the trip was changed. */
    public record ChatReply(String reply, Intent intent, List<Source> sources, Integer newVersion, TripPlan plan,
            String changeSummary) {

        public ChatReply {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        static ChatReply text(String reply, Intent intent) {
            return new ChatReply(reply, intent, List.of(), null, null, null);
        }
    }

    private final TripService trips;
    private final ConversationService conversations;
    private final IntentRouter router;
    private final AskService askService;
    private final ChangeRequestExtractor extractor;
    private final TripRevisionService revisions;

    public ChatService(TripService trips, ConversationService conversations, IntentRouter router,
            AskService askService, ChangeRequestExtractor extractor, TripRevisionService revisions) {
        this.trips = trips;
        this.conversations = conversations;
        this.router = router;
        this.askService = askService;
        this.extractor = extractor;
        this.revisions = revisions;
    }

    public ChatReply handle(UUID tripId, String guestId, String message) {
        TripRepository.TripRow trip = trips.requireOwned(tripId, guestId);
        TripPlan plan = trips.current(trip);
        ChatContext ctx = new ChatContext(tripId, trip.request(), plan, conversations.window(tripId));
        conversations.addUserMessage(tripId, message);

        IntentRouter.Routed routed = router.route(message, ctx);
        ChatReply reply = switch (routed.intent()) {
            case ASK_QUESTION -> ask(ctx, message);
            case MODIFY_PLAN -> modify(ctx, message);
            case SMALL_TALK -> ChatReply.text(SMALL_TALK_REPLY, Intent.SMALL_TALK);
            case OUT_OF_SCOPE -> ChatReply.text(OUT_OF_SCOPE_REPLY, Intent.OUT_OF_SCOPE);
        };
        conversations.addAssistantMessage(tripId, reply.reply(), reply.intent(),
                reply.newVersion() != null ? reply.newVersion() : plan.version());
        return reply;
    }

    public List<ChatMessage> history(UUID tripId, String guestId) {
        trips.requireOwned(tripId, guestId);
        return conversations.history(tripId);
    }

    private ChatReply ask(ChatContext ctx, String message) {
        AskResponse answer = askService.ask(ctx.request().destination(), message, ctx.historyText());
        return new ChatReply(answer.answer(), Intent.ASK_QUESTION, answer.sources(), null, null, null);
    }

    private ChatReply modify(ChatContext ctx, String message) {
        Optional<ChangeRequest> change = extractor.extract(message, ctx);
        if (change.isEmpty()) {
            return ChatReply.text(NOT_UNDERSTOOD_REPLY, Intent.MODIFY_PLAN);
        }
        TripRevisionService.Revision r = revisions.revise(ctx.tripId(), ctx.request(), ctx.plan(), change.get());
        String text = "Đã cập nhật lịch (phiên bản " + r.plan().version() + "): " + r.changeSummary() + "."
                + (r.plan().budget().overBudget() ? " Lưu ý: lịch mới vẫn vượt ngân sách." : "")
                + (r.plan().meta().fallback() ? " Lịch được xếp tự động vì AI chưa tạo được lịch hợp lệ." : "");
        return new ChatReply(text, Intent.MODIFY_PLAN, List.of(), r.plan().version(), r.plan(), r.changeSummary());
    }
}
