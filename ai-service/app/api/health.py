from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
def health_check():
    return {"status": "ok"}

# @router.post("/h")
# def post_check(o:str):
#     return {'status':'okayy'}