"""그래프 조립.

    intake → vision → category → price_estimation → description → END
                         ↓ (저신뢰, 1회)
                       retry ─┘(category로 복귀)

분기는 category 하나뿐이다.
"""

from functools import partial

from langgraph.graph import END, StateGraph

from app.graph.nodes import (
    category_node,
    description_node,
    intake_node,
    price_estimation_node,
    retry_node,
    route_after_category,
    vision_node,
)
from app.graph.state import DraftState
from app.llm import LLMClient
from app.schemas import ListingDraftRequest, ListingDraftResponse


def build_graph(llm: LLMClient):
    builder = StateGraph(DraftState)

    builder.add_node("intake", intake_node)
    builder.add_node("vision", partial(vision_node, llm=llm))
    builder.add_node("category", partial(category_node, llm=llm))
    builder.add_node("retry", retry_node)
    builder.add_node("price_estimation", partial(price_estimation_node, llm=llm))
    builder.add_node("description", partial(description_node, llm=llm))

    builder.set_entry_point("intake")
    builder.add_edge("intake", "vision")
    builder.add_edge("vision", "category")
    builder.add_conditional_edges(
        "category",
        route_after_category,
        {"retry": "retry", "price_estimation": "price_estimation"},
    )
    builder.add_edge("retry", "category")
    builder.add_edge("price_estimation", "description")
    builder.add_edge("description", END)

    return builder.compile()


def run_listing_draft(request: ListingDraftRequest, llm: LLMClient) -> ListingDraftResponse:
    """그래프를 실행하고 결과를 API 계약 스키마로 검증해 반환한다."""
    final_state = build_graph(llm).invoke(
        {"imageUrls": request.imageUrls, "userHint": request.userHint}
    )
    return ListingDraftResponse(
        category=final_state["category"],
        title=final_state["title"],
        description=final_state["description"],
        suggestedPrice=final_state["priceSuggestion"],
        confidence=final_state["categoryConfidence"],
    )
