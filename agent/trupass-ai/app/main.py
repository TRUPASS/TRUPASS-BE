from fastapi import FastAPI

from app.bootstrap import install_planner


def create_app() -> FastAPI:
    app = FastAPI(title="Trupass AI", version="0.1.0")
    install_planner(app)

    @app.get('/health')
    def health():
        return {'status': 'ok'}

    return app


app = create_app()
